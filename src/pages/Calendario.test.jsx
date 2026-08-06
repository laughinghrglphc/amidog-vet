import { StrictMode } from 'react'
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { BOOKING_DRAFT_KEY } from '../hooks/useBookingDraft'
import { fakeBookingApi } from '../test/fakes'
import { renderApp } from '../test/renderApp'
import Calendario from './Calendario'

const milo = {
  id: 84,
  name: 'Milo',
  species: 'Gato',
  breed: 'Mestizo',
  birthdate: '2022-05-10',
  active: true,
}

const consultation = {
  id: 7,
  code: 'consulta-general',
  name: 'Consulta general',
  description: 'Evaluación general',
  active: true,
  displayOrder: 10,
}

const baseDraft = {
  items: [{ petId: 84, serviceId: 7 }],
  startsAt: '',
  note: 'Control anual',
}

function saveDraft(draft = baseDraft) {
  sessionStorage.setItem(BOOKING_DRAFT_KEY, JSON.stringify({
    version: 1,
    draft,
  }))
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

function renderCalendar(api, route = '/calendario') {
  return renderApp(null, {
    route,
    routes: [
      { path: '/reservar', element: <h1>Reparar selección</h1> },
      { path: '/calendario', element: <Calendario api={api} /> },
      { path: '/confirmacion', element: <h1>Revisar reserva</h1> },
    ],
  })
}

beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
})

describe('Calendario', () => {
  it('completes availability loading when StrictMode replays mount effects', async () => {
    saveDraft()

    renderApp(null, {
      route: '/calendario',
      routes: [
        { path: '/reservar', element: <h1>Reparar selección</h1> },
        {
          path: '/calendario',
          element: <Calendario api={fakeBookingApi({
            pets: [milo],
            services: [consultation],
            availability: [],
          })} />,
        },
      ],
      Wrapper: StrictMode,
    })

    expect(await screen.findByText(/no hay horarios disponibles/i)).toBeVisible()
    expect(screen.queryByText(/cargando disponibilidad/i)).not.toBeInTheDocument()
  })

  it('requests only live server availability for a bounded Chilean date range', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-10T12:00:00Z'))
    try {
      saveDraft()
      const api = fakeBookingApi({
        pets: [milo],
        services: [consultation],
        availability: [],
      })
      renderCalendar(api)

      await act(async () => {
        await Promise.resolve()
        await Promise.resolve()
        await Promise.resolve()
      })

      expect(api.availability).toHaveBeenCalledWith('2026-08-10', '2026-11-07')
      expect(screen.getByText(/no hay horarios disponibles/i)).toBeVisible()
    } finally {
      vi.useRealTimers()
    }
  })

  it('shows times only after selecting an available calendar date', async () => {
    saveDraft()
    renderCalendar(fakeBookingApi({
      pets: [milo],
      services: [consultation],
      availability: [
        {
          startsAt: '2026-08-10T10:00:00-04:00',
          endsAt: '2026-08-10T10:30:00-04:00',
        },
        {
          startsAt: '2026-08-11T11:30:00-04:00',
          endsAt: '2026-08-11T12:00:00-04:00',
        },
      ],
    }))

    const august10 = await screen.findByRole('button', {
      name: /lunes,? 10 de agosto de 2026, disponible/i,
    })
    expect(screen.queryByRole('button', {
      name: /10-08-2026.*10:00/i,
    })).not.toBeInTheDocument()

    await userEvent.click(august10)
    expect(screen.getByRole('button', {
      name: /10-08-2026.*10:00/i,
    })).toBeVisible()
    expect(screen.queryByRole('button', {
      name: /11-08-2026.*11:30/i,
    })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', {
      name: /martes,? 11 de agosto de 2026, disponible/i,
    }))
    expect(screen.getByRole('button', {
      name: /11-08-2026.*11:30/i,
    })).toBeVisible()
    expect(screen.queryByRole('button', {
      name: /10-08-2026.*10:00/i,
    })).not.toBeInTheDocument()
  })

  it('groups and sorts returned slots after a Chilean calendar date is selected', async () => {
    saveDraft()
    const slots = [
      {
        startsAt: '2026-08-10T14:30:00Z',
        endsAt: '2026-08-10T15:00:00Z',
      },
      {
        startsAt: '2026-08-11T15:30:00Z',
        endsAt: '2026-08-11T16:00:00Z',
      },
      {
        startsAt: '2026-08-10T13:00:00Z',
        endsAt: '2026-08-10T13:30:00Z',
      },
    ]
    renderCalendar(fakeBookingApi({
      pets: [milo],
      services: [consultation],
      availability: slots,
    }))

    await userEvent.click(await screen.findByRole('button', {
      name: /lunes,? 10 de agosto de 2026, disponible/i,
    }))

    const timeRegion = screen.getByRole('region', { name: '2. Elige un horario' })
    expect(within(timeRegion).getAllByRole('button').map((button) => button.textContent))
      .toEqual(['09:00', '10:30'])
    expect(within(timeRegion).queryByRole('button', { name: /11-08-2026/ }))
      .not.toBeInTheDocument()
  })

  it('preserves the exact returned offset timestamp when a slot is selected', async () => {
    saveDraft()
    const startsAt = '2026-08-10T10:00:00-04:00'
    const { router } = renderCalendar(fakeBookingApi({
      pets: [milo],
      services: [consultation],
      availability: [{
        startsAt,
        endsAt: '2026-08-10T10:30:00-04:00',
      }],
    }))

    await userEvent.click(await screen.findByRole('button', {
      name: /lunes,? 10 de agosto de 2026, disponible/i,
    }))
    const slot = await screen.findByRole('button', {
      name: /10-08-2026.*10:00/i,
    })
    await userEvent.click(slot)
    expect(slot).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(screen.getByRole('button', {
      name: /continuar con la confirmación/i,
    }))

    expect(router.state.location.pathname).toBe('/confirmacion')
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft.startsAt)
      .toBe(startsAt)
  })

  it('restores a valid draft date and clears its hidden time when another date is chosen', async () => {
    const startsAt = '2026-08-10T10:00:00-04:00'
    saveDraft({ ...baseDraft, startsAt })
    renderCalendar(fakeBookingApi({
      pets: [milo],
      services: [consultation],
      availability: [
        {
          startsAt,
          endsAt: '2026-08-10T10:30:00-04:00',
        },
        {
          startsAt: '2026-08-11T11:30:00-04:00',
          endsAt: '2026-08-11T12:00:00-04:00',
        },
      ],
    }))

    expect(await screen.findByRole('button', {
      name: /lunes,? 10 de agosto de 2026, disponible/i,
    })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', {
      name: /10-08-2026.*10:00/i,
    })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', {
      name: /martes,? 11 de agosto de 2026, disponible/i,
    }))

    expect(screen.queryByRole('button', {
      name: /10-08-2026.*10:00/i,
    })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /continuar/i })).toBeDisabled()
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft.startsAt)
      .toBe('')
  })

  it('shows a closed availability failure and retries with a fresh response', async () => {
    saveDraft()
    const api = fakeBookingApi({
      pets: [milo],
      services: [consultation],
    })
    api.availability
      .mockRejectedValueOnce(new ApiError('No pudimos cargar la disponibilidad.', {
        code: 'AVAILABILITY_UNAVAILABLE',
        status: 503,
      }))
      .mockResolvedValueOnce([{
        startsAt: '2026-08-10T10:00:00-04:00',
        endsAt: '2026-08-10T10:30:00-04:00',
      }])
    renderCalendar(api)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos cargar la disponibilidad.',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

    await userEvent.click(await screen.findByRole('button', {
      name: /lunes,? 10 de agosto de 2026, disponible/i,
    }))
    expect(await screen.findByRole('button', {
      name: /10-08-2026.*10:00/i,
    })).toBeVisible()
    expect(api.availability).toHaveBeenCalledTimes(2)
  })

  it('ignores a stale refresh response that resolves after the newer result', async () => {
    saveDraft()
    const stale = deferred()
    const freshSlot = {
      startsAt: '2026-08-11T11:30:00-04:00',
      endsAt: '2026-08-11T12:00:00-04:00',
    }
    const staleSlot = {
      startsAt: '2026-08-10T09:00:00-04:00',
      endsAt: '2026-08-10T09:30:00-04:00',
    }
    const api = fakeBookingApi({
      pets: [milo],
      services: [consultation],
    })
    api.availability
      .mockReturnValueOnce(stale.promise)
      .mockResolvedValueOnce([freshSlot])
    renderCalendar(api)

    await waitFor(() => expect(api.availability).toHaveBeenCalledTimes(1))
    await userEvent.click(screen.getByRole('button', {
      name: 'Actualizar disponibilidad',
    }))
    await userEvent.click(await screen.findByRole('button', {
      name: /martes,? 11 de agosto de 2026, disponible/i,
    }))
    expect(await screen.findByRole('button', {
      name: /11-08-2026.*11:30/i,
    })).toBeVisible()

    await act(async () => {
      stale.resolve([staleSlot])
      await stale.promise
    })

    expect(screen.queryByRole('button', {
      name: /10-08-2026.*09:00/i,
    })).not.toBeInTheDocument()
    expect(screen.getByRole('button', {
      name: /11-08-2026.*11:30/i,
    })).toBeVisible()
  })

  it('clears a selected slot that disappeared while preserving items and note', async () => {
    saveDraft({
      ...baseDraft,
      startsAt: '2026-08-10T10:00:00-04:00',
    })
    renderCalendar(fakeBookingApi({
      pets: [milo],
      services: [consultation],
      availability: [{
        startsAt: '2026-08-10T10:30:00-04:00',
        endsAt: '2026-08-10T11:00:00-04:00',
      }],
    }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /horario seleccionado ya no está disponible/i,
    )
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft).toEqual({
      ...baseDraft,
      startsAt: '',
    })
  })

  it('immediately clears a live selected slot when a refresh removes it', async () => {
    saveDraft()
    const startsAt = '2026-08-10T10:00:00-04:00'
    const api = fakeBookingApi({
      pets: [milo],
      services: [consultation],
    })
    api.availability
      .mockResolvedValueOnce([{
        startsAt,
        endsAt: '2026-08-10T10:30:00-04:00',
      }])
      .mockResolvedValueOnce([])
    renderCalendar(api)

    await userEvent.click(await screen.findByRole('button', {
      name: /lunes,? 10 de agosto de 2026, disponible/i,
    }))
    const slot = await screen.findByRole('button', {
      name: /10-08-2026.*10:00/i,
    })
    await userEvent.click(slot)

    expect(slot).toHaveAttribute('aria-pressed', 'true')
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft).toEqual({
      ...baseDraft,
      startsAt,
    })

    await userEvent.click(screen.getByRole('button', {
      name: 'Actualizar disponibilidad',
    }))

    expect(await screen.findByText(/no hay horarios disponibles/i)).toBeVisible()
    expect(screen.getByRole('button', {
      name: /continuar/i,
    })).toBeDisabled()
    expect(screen.getByRole('alert')).toHaveTextContent(
      /horario seleccionado ya no/i,
    )
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft).toEqual({
      ...baseDraft,
      startsAt: '',
    })
  })

  it('does not let a superseded refresh clear a newer valid live selection', async () => {
    saveDraft()
    const staleRefresh = deferred()
    const firstSlot = {
      startsAt: '2026-08-10T10:00:00-04:00',
      endsAt: '2026-08-10T10:30:00-04:00',
    }
    const newerSlot = {
      startsAt: '2026-08-11T11:30:00-04:00',
      endsAt: '2026-08-11T12:00:00-04:00',
    }
    const api = fakeBookingApi({
      pets: [milo],
      services: [consultation],
    })
    api.availability
      .mockResolvedValueOnce([firstSlot])
      .mockReturnValueOnce(staleRefresh.promise)
      .mockResolvedValueOnce([newerSlot])
    renderCalendar(api)

    await userEvent.click(await screen.findByRole('button', {
      name: /lunes,? 10 de agosto de 2026, disponible/i,
    }))
    const initial = await screen.findByRole('button', {
      name: /10-08-2026.*10:00/i,
    })
    await userEvent.click(initial)
    const refresh = screen.getByRole('button', {
      name: 'Actualizar disponibilidad',
    })
    await userEvent.click(refresh)
    await waitFor(() => expect(api.availability).toHaveBeenCalledTimes(2))
    await userEvent.click(refresh)

    await userEvent.click(await screen.findByRole('button', {
      name: /martes,? 11 de agosto de 2026, disponible/i,
    }))
    const newer = await screen.findByRole('button', {
      name: /11-08-2026.*11:30/i,
    })
    await userEvent.click(newer)
    expect(newer).toHaveAttribute('aria-pressed', 'true')

    await act(async () => {
      staleRefresh.resolve([])
      await staleRefresh.promise
    })

    expect(newer).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', {
      name: /continuar/i,
    })).toBeEnabled()
    expect(screen.queryByText(
      /horario seleccionado ya no/i,
    )).not.toBeInTheDocument()
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft.startsAt)
      .toBe(newerSlot.startsAt)
  })

  it('redirects an incomplete or stale draft to the pet/service step without loading slots', async () => {
    saveDraft({
      items: [{ petId: 999, serviceId: 7 }],
      startsAt: '',
      note: '',
    })
    const api = fakeBookingApi({
      pets: [milo],
      services: [consultation],
    })
    const { router } = renderCalendar(api)

    expect(await screen.findByRole('heading', { name: 'Reparar selección' }))
      .toBeVisible()
    expect(router.state.location.pathname).toBe('/reservar')
    expect(api.availability).not.toHaveBeenCalled()
  })

  it('does not read or write the booking flow through localStorage', async () => {
    saveDraft()
    const localGet = vi.spyOn(localStorage, 'getItem')
    const localSet = vi.spyOn(localStorage, 'setItem')
    renderCalendar(fakeBookingApi({
      pets: [milo],
      services: [consultation],
      availability: [],
    }))

    expect(await screen.findByText(/no hay horarios disponibles/i)).toBeVisible()
    expect(localGet).not.toHaveBeenCalled()
    expect(localSet).not.toHaveBeenCalled()
  })
})

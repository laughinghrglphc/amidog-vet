import { StrictMode } from 'react'
import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { BOOKING_DRAFT_KEY } from '../hooks/useBookingDraft'
import { fakeBookingApi } from '../test/fakes'
import { renderApp } from '../test/renderApp'
import Calendario from './Calendario'
import Confirmacion from './Confirmacion'

const milo = {
  id: 84,
  name: 'Milo actualizado',
  species: 'Gato',
  breed: 'Mestizo',
  birthdate: '2022-05-10',
  active: true,
}

const luna = {
  id: 85,
  name: 'Luna actualizada',
  species: 'Perro',
  breed: null,
  birthdate: null,
  active: true,
}

const consultation = {
  id: 7,
  code: 'consulta-general',
  name: 'Consulta general actualizada',
  description: 'Evaluación general',
  active: true,
  displayOrder: 10,
}

const vaccine = {
  id: 9,
  code: 'vacunacion',
  name: 'Vacunación actualizada',
  description: null,
  active: true,
  displayOrder: 20,
}

const validDraft = {
  items: [
    { petId: 84, serviceId: 7 },
    { petId: 85, serviceId: 9 },
  ],
  startsAt: '2026-08-10T10:00:00-04:00',
  note: 'Control anual',
}

function saveDraft(draft = validDraft, extras = {}) {
  sessionStorage.setItem(BOOKING_DRAFT_KEY, JSON.stringify({
    version: 1,
    draft: { ...draft, ...extras },
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

function confirmationApi(status = 'PENDING') {
  return fakeBookingApi({
    pets: [milo, luna],
    services: [consultation, vaccine],
    reservation: {
      id: 301,
      startsAt: validDraft.startsAt,
      endsAt: '2026-08-10T10:30:00-04:00',
      status,
      note: 'Control anual',
      items: [
        {
          petId: 84,
          petName: 'Milo actualizado',
          serviceId: 7,
          serviceName: 'Consulta general actualizada',
        },
        {
          petId: 85,
          petName: 'Luna actualizada',
          serviceId: 9,
          serviceName: 'Vacunación actualizada',
        },
      ],
      createdAt: '2026-08-01T09:15:00-04:00',
    },
  })
}

function renderConfirmation(api, {
  includeCalendar = false,
} = {}) {
  return renderApp(null, {
    route: '/confirmacion',
    routes: [
      { path: '/reservar', element: <h1>Reparar mascotas y servicios</h1> },
      {
        path: '/calendario',
        element: includeCalendar
          ? <Calendario api={api} />
          : <h1>Elegir otro horario</h1>,
      },
      { path: '/confirmacion', element: <Confirmacion api={api} /> },
      { path: '/panel', element: <h1>Panel del cliente</h1> },
    ],
  })
}

async function loadConfirmation(api = confirmationApi()) {
  const rendered = renderConfirmation(api)
  expect(await screen.findByRole('heading', { name: /revisa tu reserva/i }))
    .toBeVisible()
  expect(await screen.findByRole('button', { name: 'Confirmar reserva' }))
    .toBeVisible()
  return { api, ...rendered }
}

beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
})

describe('Confirmacion', () => {
  it('completes name loading when StrictMode replays mount effects', async () => {
    saveDraft()

    renderApp(null, {
      route: '/confirmacion',
      routes: [
        { path: '/reservar', element: <h1>Reparar mascotas y servicios</h1> },
        { path: '/calendario', element: <h1>Elegir otro horario</h1> },
        {
          path: '/confirmacion',
          element: <Confirmacion api={confirmationApi()} />,
        },
      ],
      Wrapper: StrictMode,
    })

    expect(await screen.findByRole('button', { name: 'Confirmar reserva' }))
      .toBeVisible()
    expect(screen.queryByText(/revisando los datos actuales/i))
      .not.toBeInTheDocument()
  })

  it('resolves current pet/service names and Chilean date-time instead of trusting persisted labels', async () => {
    saveDraft(validDraft, {
      petNames: ['Nombre antiguo'],
      serviceNames: ['Servicio antiguo'],
      email: 'ana@example.cl',
    })
    await loadConfirmation()

    expect(screen.getByText('Milo actualizado')).toBeVisible()
    expect(screen.getByText('Consulta general actualizada')).toBeVisible()
    expect(screen.getByText('Luna actualizada')).toBeVisible()
    expect(screen.getByText('Vacunación actualizada')).toBeVisible()
    expect(screen.getByText('10-08-2026')).toBeVisible()
    expect(screen.getByText('10:00')).toBeVisible()
    expect(screen.queryByText('Nombre antiguo')).not.toBeInTheDocument()
    expect(screen.queryByText('ana@example.cl')).not.toBeInTheDocument()
  })

  it('redirects missing items to step one and a missing slot to availability', async () => {
    saveDraft({ items: [], startsAt: '', note: '' })
    let api = confirmationApi()
    let rendered = renderConfirmation(api)
    expect(await screen.findByRole('heading', {
      name: 'Reparar mascotas y servicios',
    })).toBeVisible()
    expect(rendered.router.state.location.pathname).toBe('/reservar')
    expect(api.createReservation).not.toHaveBeenCalled()

    rendered.unmount()
    saveDraft({ ...validDraft, startsAt: '' })
    api = confirmationApi()
    rendered = renderConfirmation(api)
    expect(await screen.findByRole('heading', { name: 'Elegir otro horario' }))
      .toBeVisible()
    expect(rendered.router.state.location.pathname).toBe('/calendario')
    expect(api.createReservation).not.toHaveBeenCalled()
  })

  it('redirects IDs that are no longer active to the pet/service step', async () => {
    saveDraft()
    const api = confirmationApi()
    api.pets.mockResolvedValueOnce([{ ...milo, active: false }, luna])
    const { router } = renderConfirmation(api)

    expect(await screen.findByRole('heading', {
      name: 'Reparar mascotas y servicios',
    })).toBeVisible()
    expect(router.state.location.pathname).toBe('/reservar')
    expect(api.createReservation).not.toHaveBeenCalled()
  })

  it('submits the exact draft once even under rapid clicks', async () => {
    saveDraft()
    const request = deferred()
    const api = confirmationApi()
    api.createReservation.mockReturnValueOnce(request.promise)
    await loadConfirmation(api)
    const confirm = screen.getByRole('button', { name: 'Confirmar reserva' })

    fireEvent.click(confirm)
    fireEvent.click(confirm)

    expect(api.createReservation).toHaveBeenCalledTimes(1)
    expect(api.createReservation).toHaveBeenCalledWith(validDraft)
    expect(confirm).toBeDisabled()

    await act(async () => {
      request.resolve({
        id: 301,
        startsAt: validDraft.startsAt,
        endsAt: '2026-08-10T10:30:00-04:00',
        status: 'PENDING',
        note: validDraft.note,
        items: [],
        createdAt: '2026-08-01T09:15:00-04:00',
      })
      await request.promise
    })
  })

  it('clears the draft once and shows the PENDING outcome without promising external messages', async () => {
    saveDraft()
    const clearSpy = vi.spyOn(Storage.prototype, 'removeItem')
    await loadConfirmation(confirmationApi('PENDING'))

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Recibimos tu solicitud y está pendiente de confirmación.',
    )
    expect(screen.getByText(/revisa el estado en tu panel y notificaciones/i))
      .toBeVisible()
    expect(screen.queryByText(/whatsapp|te enviaremos.*correo|te contactaremos/i))
      .not.toBeInTheDocument()
    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).toBeNull()
    expect(clearSpy.mock.calls.filter(([key]) => key === BOOKING_DRAFT_KEY))
      .toHaveLength(1)
  })

  it('durably clears the draft once when creation succeeds after unmount', async () => {
    saveDraft()
    const request = deferred()
    const api = confirmationApi()
    api.createReservation.mockReturnValueOnce(request.promise)
    const clearSpy = vi.spyOn(Storage.prototype, 'removeItem')
    const { router } = await loadConfirmation(api)

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))
    await userEvent.click(screen.getByRole('button', { name: /volver al horario/i }))
    expect(router.state.location.pathname).toBe('/calendario')

    await act(async () => {
      request.resolve({
        id: 301,
        startsAt: validDraft.startsAt,
        status: 'PENDING',
      })
      await request.promise
    })

    expect(router.state.location.pathname).toBe('/calendario')
    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).toBeNull()
    expect(clearSpy.mock.calls.filter(([key]) => key === BOOKING_DRAFT_KEY))
      .toHaveLength(1)
  })

  it('clears a reconciled persisted draft when the earlier creation later succeeds', async () => {
    saveDraft()
    const request = deferred()
    const api = confirmationApi()
    api.createReservation.mockReturnValueOnce(request.promise)
    const clearSpy = vi.spyOn(Storage.prototype, 'removeItem')
    await loadConfirmation(api)

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))
    await userEvent.click(screen.getByRole('button', { name: /volver al horario/i }))
    saveDraft({ ...validDraft, startsAt: '' })

    await act(async () => {
      request.resolve({
        id: 301,
        startsAt: validDraft.startsAt,
        status: 'PENDING',
      })
      await request.promise
    })

    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).toBeNull()
    expect(clearSpy.mock.calls.filter(([key]) => key === BOOKING_DRAFT_KEY))
      .toHaveLength(1)
  })

  it('uses the distinct CONFIRMED outcome returned by the switch', async () => {
    saveDraft()
    await loadConfirmation(confirmationApi('CONFIRMED'))

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Tu reserva quedó confirmada.',
    )
    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).toBeNull()
  })

  it('keeps the full draft and error on confirmation after a generic closed API failure', async () => {
    saveDraft()
    const api = confirmationApi()
    api.createReservation.mockRejectedValueOnce(new ApiError(
      'No pudimos registrar la reserva.',
      { code: 'INTERNAL_ERROR', status: 500 },
    ))
    await loadConfirmation(api)

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos registrar la reserva.',
    )
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft)
      .toEqual(validDraft)
    expect(screen.getByRole('button', { name: 'Confirmar reserva' })).toBeEnabled()
  })

  it('clears only the colliding start, returns to availability, and forces a live refresh', async () => {
    saveDraft()
    const api = confirmationApi()
    api.createReservation.mockRejectedValueOnce(new ApiError(
      'Ese horario acaba de ser reservado. Elige otro bloque disponible.',
      { code: 'SLOT_ALREADY_BOOKED', status: 409 },
    ))
    api.availability.mockResolvedValueOnce([{
      startsAt: '2026-08-10T10:30:00-04:00',
      endsAt: '2026-08-10T11:00:00-04:00',
    }])
    const { router } = renderConfirmation(api, { includeCalendar: true })
    expect(await screen.findByRole('heading', { name: /revisa tu reserva/i }))
      .toBeVisible()

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ese horario acaba de ser reservado. Elige otro bloque disponible.',
    )
    await waitFor(() => expect(api.availability).toHaveBeenCalledTimes(1))
    expect(router.state.location.pathname).toBe('/calendario')
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft).toEqual({
      items: validDraft.items,
      startsAt: '',
      note: validDraft.note,
    })
    expect(api.pets).toHaveBeenCalledTimes(2)
    expect(api.services).toHaveBeenCalledTimes(2)
  })

  it('durably clears only the colliding start when the 409 arrives after unmount', async () => {
    saveDraft()
    const request = deferred()
    const api = confirmationApi()
    api.createReservation.mockReturnValueOnce(request.promise)
    const { router } = await loadConfirmation(api)

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))
    await userEvent.click(screen.getByRole('button', { name: /volver al horario/i }))
    expect(router.state.location.pathname).toBe('/calendario')

    await act(async () => {
      request.reject(new ApiError(
        'Ese horario acaba de ser reservado. Elige otro bloque disponible.',
        { code: 'SLOT_ALREADY_BOOKED', status: 409 },
      ))
      await request.promise.catch(() => undefined)
    })

    expect(router.state.location.pathname).toBe('/calendario')
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft).toEqual({
      items: validDraft.items,
      startsAt: '',
      note: validDraft.note,
    })
  })

  it('preserves the draft when a generic failure arrives after unmount', async () => {
    saveDraft()
    const request = deferred()
    const api = confirmationApi()
    api.createReservation.mockReturnValueOnce(request.promise)
    const { router } = await loadConfirmation(api)

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))
    await userEvent.click(screen.getByRole('button', { name: /volver al horario/i }))

    await act(async () => {
      request.reject(new ApiError(
        'No pudimos registrar la reserva.',
        { code: 'INTERNAL_ERROR', status: 500 },
      ))
      await request.promise.catch(() => undefined)
    })

    expect(router.state.location.pathname).toBe('/calendario')
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft)
      .toEqual(validDraft)
  })

  it('shows a retryable current-name load failure and ignores completion after unmount', async () => {
    saveDraft()
    const pendingPets = deferred()
    const api = confirmationApi()
    api.pets
      .mockRejectedValueOnce(new ApiError('No pudimos revisar tus mascotas.', {
        code: 'PETS_UNAVAILABLE',
        status: 503,
      }))
      .mockReturnValueOnce(pendingPets.promise)
    const rendered = renderConfirmation(api)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos revisar tus mascotas.',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    await waitFor(() => expect(api.pets).toHaveBeenCalledTimes(2))
    rendered.unmount()

    await act(async () => {
      pendingPets.resolve([milo, luna])
      await pendingPets.promise
    })
    expect(api.createReservation).not.toHaveBeenCalled()
  })

  it('never dispatches the obsolete confirmation event or touches localStorage', async () => {
    saveDraft()
    const eventListener = vi.fn()
    document.addEventListener('amidog:reserva-confirmada', eventListener)
    const localWrite = vi.spyOn(localStorage, 'setItem')
    await loadConfirmation(confirmationApi('PENDING'))

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))
    await screen.findByText(/pendiente de confirmación/i)

    expect(eventListener).not.toHaveBeenCalled()
    expect(localWrite).not.toHaveBeenCalled()
    document.removeEventListener('amidog:reserva-confirmada', eventListener)
  })
})

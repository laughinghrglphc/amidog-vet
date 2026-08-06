import { StrictMode, useState } from 'react'
import { act, fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import {
  BOOKING_DRAFT_KEY,
  LEGACY_BOOKING_DRAFT_KEY,
} from '../hooks/useBookingDraft'
import { fakeBookingApi } from '../test/fakes'
import { renderApp } from '../test/renderApp'
import Reservation from './Reservation'

const milo = {
  id: 84,
  name: 'Milo',
  species: 'Gato',
  breed: 'Mestizo',
  birthdate: '2022-05-10',
  active: true,
}

const luna = {
  id: 85,
  name: 'Luna',
  species: 'Perro',
  breed: null,
  birthdate: null,
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

const vaccine = {
  id: 9,
  code: 'vacunacion',
  name: 'Vacunación',
  description: null,
  active: true,
  displayOrder: 20,
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

function renderReservation(api) {
  return renderApp(null, {
    route: '/reservar',
    routes: [
      { path: '/reservar', element: <Reservation api={api} /> },
      { path: '/calendario', element: <h1>Disponibilidad en vivo</h1> },
      { path: '/panel', element: <h1>Panel de mascotas</h1> },
    ],
  })
}

async function loadReservation(api = fakeBookingApi({
  pets: [milo, luna],
  services: [consultation, vaccine],
})) {
  const rendered = renderReservation(api)
  expect(await screen.findByRole('heading', {
    name: /elige tus mascotas y servicios/i,
  })).toBeVisible()
  expect(await screen.findByRole('group', { name: 'Mascotas registradas' }))
    .toBeVisible()
  return { api, ...rendered }
}

beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
})

describe('Reservation', () => {
  it('completes resource loading when StrictMode replays mount effects', async () => {
    const api = fakeBookingApi({
      pets: [milo],
      services: [consultation],
    })

    renderApp(null, {
      route: '/reservar',
      routes: [{
        path: '/reservar',
        element: <Reservation api={api} />,
      }],
      Wrapper: StrictMode,
    })

    expect(await screen.findByLabelText('Milo')).toBeVisible()
    expect(screen.queryByText(/cargando mascotas y servicios/i))
      .not.toBeInTheDocument()
  })

  it('loads active registered pets and services with an accessible loading state', async () => {
    const petsRequest = deferred()
    const servicesRequest = deferred()
    const api = fakeBookingApi()
    api.pets.mockReturnValueOnce(petsRequest.promise)
    api.services.mockReturnValueOnce(servicesRequest.promise)

    renderReservation(api)
    expect(screen.getByRole('status')).toHaveTextContent(/cargando mascotas y servicios/i)

    await act(async () => {
      petsRequest.resolve([milo])
      servicesRequest.resolve([consultation])
      await Promise.all([petsRequest.promise, servicesRequest.promise])
    })

    expect(await screen.findByLabelText('Milo')).toBeVisible()
    expect(api.pets).toHaveBeenCalledTimes(1)
    expect(api.services).toHaveBeenCalledTimes(1)
  })

  it('shows a closed load error and retries both current resources', async () => {
    const api = fakeBookingApi({ pets: [milo], services: [consultation] })
    api.pets
      .mockRejectedValueOnce(new ApiError('No pudimos cargar tus mascotas.', {
        code: 'PETS_UNAVAILABLE',
        status: 503,
      }))
      .mockResolvedValueOnce([milo])

    renderReservation(api)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos cargar tus mascotas.',
    )

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByLabelText('Milo')).toBeVisible()
    expect(api.pets).toHaveBeenCalledTimes(2)
    expect(api.services).toHaveBeenCalledTimes(2)
  })

  it('directs a client with no active pets to register one in the panel', async () => {
    const api = fakeBookingApi({ pets: [], services: [consultation] })
    const { router } = renderReservation(api)

    expect(await screen.findByText(/no tienes mascotas activas registradas/i))
      .toBeVisible()
    await userEvent.click(screen.getByRole('link', { name: /registrar una mascota/i }))

    expect(router.state.location.pathname).toBe('/panel')
  })

  it('explains that booking is unavailable when the active service catalog is empty', async () => {
    renderReservation(fakeBookingApi({ pets: [milo], services: [] }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /no hay servicios disponibles/i,
    )
    expect(screen.queryByRole('button', { name: /continuar/i })).toBeDisabled()
  })

  it('requires one service for each selected pet and preserves the other selection', async () => {
    await loadReservation()

    await userEvent.click(screen.getByLabelText('Milo'))
    await userEvent.selectOptions(
      screen.getByLabelText('Servicio para Milo'),
      String(consultation.id),
    )
    await userEvent.click(screen.getByLabelText('Luna'))
    await userEvent.click(screen.getByRole('button', { name: /continuar con el horario/i }))

    expect(screen.getByRole('alert')).toHaveTextContent(/servicio para Luna/i)
    expect(screen.getByLabelText('Milo')).toBeChecked()
    expect(screen.getByLabelText('Luna')).toBeChecked()
    expect(screen.getByLabelText('Servicio para Milo')).toHaveValue('7')

    await userEvent.click(screen.getByLabelText('Luna'))
    expect(screen.queryByLabelText('Servicio para Luna')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Servicio para Milo')).toHaveValue('7')
  })

  it('enforces the ten-distinct-pet maximum without dropping existing choices', async () => {
    const pets = Array.from({ length: 11 }, (_, index) => ({
      ...milo,
      id: index + 1,
      name: `Mascota ${index + 1}`,
    }))
    await loadReservation(fakeBookingApi({
      pets,
      services: [consultation],
    }))

    for (const pet of pets.slice(0, 10)) {
      await userEvent.click(screen.getByLabelText(pet.name))
    }
    await userEvent.click(screen.getByLabelText('Mascota 11'))

    expect(screen.getByRole('alert')).toHaveTextContent(/máximo 10 mascotas/i)
    expect(screen.getByLabelText('Mascota 1')).toBeChecked()
    expect(screen.getByLabelText('Mascota 10')).toBeChecked()
    expect(screen.getByLabelText('Mascota 11')).not.toBeChecked()
  })

  it('validates and normalizes the optional 500-character note', async () => {
    await loadReservation(fakeBookingApi({
      pets: [milo],
      services: [consultation],
    }))
    await userEvent.click(screen.getByLabelText('Milo'))
    await userEvent.selectOptions(
      screen.getByLabelText('Servicio para Milo'),
      '7',
    )
    fireEvent.change(screen.getByLabelText('Nota para la clínica (opcional)'), {
      target: { value: 'a'.repeat(501) },
    })
    await userEvent.click(screen.getByRole('button', { name: /continuar/i }))

    expect(screen.getByRole('alert')).toHaveTextContent(/máximo 500 caracteres/i)
  })

  it('stores only IDs and the normalized note, then continues without localStorage', async () => {
    const localWrite = vi.spyOn(localStorage, 'setItem')
    const { router } = await loadReservation()
    await userEvent.click(screen.getByLabelText('Milo'))
    await userEvent.selectOptions(screen.getByLabelText('Servicio para Milo'), '7')
    await userEvent.click(screen.getByLabelText('Luna'))
    await userEvent.selectOptions(screen.getByLabelText('Servicio para Luna'), '9')
    await userEvent.type(
      screen.getByLabelText('Nota para la clínica (opcional)'),
      '  Control anual  ',
    )

    await userEvent.click(screen.getByRole('button', {
      name: /continuar con el horario/i,
    }))

    expect(router.state.location.pathname).toBe('/calendario')
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY))).toEqual({
      version: 1,
      draft: {
        items: [
          { petId: 84, serviceId: 7 },
          { petId: 85, serviceId: 9 },
        ],
        startsAt: '',
        note: 'Control anual',
      },
    })
    expect(localWrite).not.toHaveBeenCalled()
  })

  it('clears legacy PII and renders no typed contact, pet, veterinarian, or preferred-time fields', async () => {
    sessionStorage.setItem(LEGACY_BOOKING_DRAFT_KEY, JSON.stringify({
      nombre: 'Ana Pérez',
      correo: 'ana@example.cl',
      telefono: '+56912345678',
    }))
    await loadReservation()

    expect(sessionStorage.getItem(LEGACY_BOOKING_DRAFT_KEY)).toBeNull()
    expect(screen.queryByLabelText(/correo/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/teléfono/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/nombre de la mascota/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/profesional|veterinari/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/fecha preferida|horario preferido/i))
      .not.toBeInTheDocument()
  })

  it('ignores a stale resource load after a newer API instance replaces it', async () => {
    const oldPets = deferred()
    const oldApi = fakeBookingApi({ services: [consultation] })
    oldApi.pets.mockReturnValueOnce(oldPets.promise)
    const currentApi = fakeBookingApi({
      pets: [luna],
      services: [vaccine],
    })
    function ApiSwapHarness() {
      const [api, setApi] = useState(oldApi)
      return (
        <>
          <button type="button" onClick={() => setApi(currentApi)}>Cambiar API</button>
          <Reservation api={api} />
        </>
      )
    }
    renderApp(<ApiSwapHarness />, { route: '/reservar' })
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar API' }))
    expect(await screen.findByLabelText('Luna')).toBeVisible()

    await act(async () => {
      oldPets.resolve([milo])
      await oldPets.promise
    })

    expect(screen.queryByLabelText('Milo')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Luna')).toBeVisible()
  })
})

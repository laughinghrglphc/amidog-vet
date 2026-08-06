import {
  act,
  fireEvent,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { AuthProvider } from '../auth/AuthProvider'
import { fakeAuth, fakeClientApi } from '../test/fakes'
import { renderApp } from '../test/renderApp'
import Panel from './Panel'

const account = {
  userId: 41,
  clientId: 12,
  email: 'ana@example.cl',
  name: 'Ana Pérez',
  accountType: 'CLIENT',
}

const profile = {
  id: 12,
  name: 'Ana Pérez',
  phone: '+56912345678',
  email: 'ana@example.cl',
}

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

function reservation({
  id,
  status,
  startsAt = '2099-08-10T10:00:00-04:00',
  items = [{
    petId: milo.id,
    petName: milo.name,
    serviceId: 7,
    serviceName: 'Consulta general',
  }],
} = {}) {
  return {
    id,
    startsAt,
    endsAt: '2099-08-10T10:30:00-04:00',
    status,
    note: null,
    items,
    createdAt: '2026-08-01T09:15:00-04:00',
  }
}

const unreadNotification = {
  id: 901,
  type: 'APPOINTMENT_REMINDER',
  title: 'Recordatorio de reserva',
  body: 'Tu reserva se acerca.',
  reservationId: 301,
  createdAt: '2026-08-02T15:10:00Z',
  unread: true,
}

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function authWrapper() {
  const authApi = fakeAuth({ me: account })
  return function PanelAuthWrapper({ children }) {
    return <AuthProvider api={authApi}>{children}</AuthProvider>
  }
}

function renderPanel(api, route = '/panel') {
  const availabilityApi = {
    availability: vi.fn().mockResolvedValue([{
      startsAt: '2099-08-11T11:30:00-04:00',
      endsAt: '2099-08-11T12:00:00-04:00',
    }]),
  }
  return renderApp(null, {
    route,
    routes: [
      { path: '/panel', element: <Panel api={api} availabilityApi={availabilityApi} /> },
      { path: '/reservar', element: <h1>Reservar una hora</h1> },
    ],
    Wrapper: authWrapper(),
  })
}

function renderPanelWithAvailability(api, availabilityApi, route = '/panel') {
  return renderApp(null, {
    route,
    routes: [
      { path: '/panel', element: <Panel api={api} availabilityApi={availabilityApi} /> },
      { path: '/reservar', element: <h1>Reservar una hora</h1> },
    ],
    Wrapper: authWrapper(),
  })
}

async function waitForPanel() {
  expect(await screen.findByRole('heading', { name: 'Tus horas' })).toBeVisible()
  await waitFor(() => {
    expect(screen.queryByText('Cargando tu panel…')).not.toBeInTheDocument()
  })
}

async function openProfileForm() {
  await userEvent.click(await screen.findByRole('button', { name: /ana pérez/i }))
  await userEvent.click(screen.getByRole('button', { name: 'Mi perfil' }))
}

async function openPetDetail(name = 'Milo') {
  await userEvent.click(await screen.findByRole('button', {
    name: `Ver ficha de ${name}`,
  }))
}

describe('Panel', () => {
  it('renders real multi-pet history and all five backend statuses without browser persistence', async () => {
    const storageRead = vi.spyOn(Storage.prototype, 'getItem')
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    const reservations = [
      reservation({
        id: 301,
        status: 'PENDING',
        items: [
          {
            petId: milo.id,
            petName: milo.name,
            serviceId: 7,
            serviceName: 'Consulta general',
          },
          {
            petId: luna.id,
            petName: luna.name,
            serviceId: 9,
            serviceName: 'Vacunación',
          },
        ],
      }),
      reservation({ id: 302, status: 'CONFIRMED', startsAt: '2099-12-10T10:00:00-03:00' }),
      reservation({ id: 303, status: 'CANCELLED' }),
      reservation({ id: 304, status: 'COMPLETED' }),
      reservation({ id: 305, status: 'NO_SHOW' }),
    ]
    const api = fakeClientApi({
      profile,
      pets: [milo, luna],
      reservations,
    })

    renderPanel(api)
    await waitForPanel()

    expect(screen.getAllByText('Milo').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Luna').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Consulta general').length).toBeGreaterThan(0)
    expect(screen.getByText('Vacunación')).toBeVisible()
    for (const label of ['Pendiente', 'Confirmada', 'Cancelada', 'Completada', 'No asistió']) {
      expect(screen.getByText(label)).toBeVisible()
    }
    expect(screen.getAllByText('10-08-2099').length).toBeGreaterThan(0)
    expect(screen.getByText('10-12-2099')).toBeVisible()
    expect(screen.queryByText(/veterinario/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/sano|tratamiento|vacunas al día|esterilizad/i)).not.toBeInTheDocument()
    expect(storageRead).not.toHaveBeenCalledWith('amidog-pets')
    expect(storageRead).not.toHaveBeenCalledWith('amidog-appointments')
    expect(storageRead).not.toHaveBeenCalledWith('amidog-profile')
    expect(storageRead).not.toHaveBeenCalledWith('amidog-notifications')
    expect(storageWrite).not.toHaveBeenCalled()
  })

  it('shows distinct empty states and retries an initial load failure', async () => {
    const api = fakeClientApi({ profile })
    api.profile
      .mockRejectedValueOnce(new ApiError('No pudimos cargar tu perfil.', {
        code: 'PROFILE_UNAVAILABLE',
        status: 503,
      }))
      .mockResolvedValueOnce(profile)

    renderPanel(api)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos cargar tu perfil.',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByText('No tienes mascotas activas registradas.')).toBeVisible()
    expect(screen.getByText('No tienes reservas registradas.')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: /notificaciones/i }))
    expect(screen.getByText('No tienes notificaciones.')).toBeVisible()
    expect(api.profile).toHaveBeenCalledTimes(2)
  })

  it('updates the real profile with validated trimmed fields and keeps email read-only', async () => {
    const updated = { ...profile, name: 'Ana María', phone: '+56987654321' }
    const api = fakeClientApi({ profile })
    api.updateProfile.mockResolvedValueOnce(updated)
    renderPanel(api)
    await waitForPanel()
    await openProfileForm()

    const email = screen.getByLabelText('Correo electrónico')
    expect(email).toHaveValue(profile.email)
    expect(email).toHaveAttribute('readonly')
    await userEvent.clear(screen.getByLabelText('Nombre'))
    await userEvent.type(screen.getByLabelText('Nombre'), '  Ana María  ')
    await userEvent.clear(screen.getByLabelText('Teléfono'))
    await userEvent.type(screen.getByLabelText('Teléfono'), '  +56987654321  ')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar perfil' }))

    await waitFor(() => {
      expect(api.updateProfile).toHaveBeenCalledWith({
        name: 'Ana María',
        phone: '+56987654321',
      })
    })
    expect(screen.getByRole('status')).toHaveTextContent('Tu perfil fue actualizado.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ana María' })).toBeVisible()
  })

  it('keeps the profile dialog open with a safe backend failure and validates blanks', async () => {
    const api = fakeClientApi({ profile })
    api.updateProfile.mockRejectedValueOnce(new ApiError(
      'El teléfono no es válido.',
      { code: 'VALIDATION_ERROR', status: 400, errors: { phone: 'Formato inválido.' } },
    ))
    renderPanel(api)
    await waitForPanel()
    await openProfileForm()

    await userEvent.clear(screen.getByLabelText('Nombre'))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar perfil' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Ingresa tu nombre.')
    expect(api.updateProfile).not.toHaveBeenCalled()

    await userEvent.type(screen.getByLabelText('Nombre'), 'Ana Pérez')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar perfil' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El teléfono no es válido.',
    )
    expect(screen.getByRole('dialog')).toBeVisible()
  })

  it('creates a pet with exact optional null fields and rejects a future birthdate', async () => {
    const api = fakeClientApi({ profile })
    api.pets.mockResolvedValueOnce([]).mockResolvedValueOnce([luna])
    renderPanel(api)
    await waitForPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Registrar nueva mascota' }))

    await userEvent.type(screen.getByLabelText('Nombre'), 'Luna')
    await userEvent.clear(screen.getByLabelText('Especie'))
    await userEvent.type(screen.getByLabelText('Especie'), 'Perro')
    fireEvent.change(screen.getByLabelText('Fecha de nacimiento (opcional)'), {
      target: { value: '2099-01-01' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Registrar mascota' }))
    expect(screen.getByRole('alert')).toHaveTextContent(
      'La fecha de nacimiento no puede estar en el futuro.',
    )
    expect(api.createPet).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Fecha de nacimiento (opcional)'), {
      target: { value: '' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Registrar mascota' }))

    await waitFor(() => {
      expect(api.createPet).toHaveBeenCalledWith({
        name: 'Luna',
        species: 'Perro',
        breed: null,
        birthdate: null,
      })
    })
    expect(await screen.findByText('Luna')).toBeVisible()
  })

  it('edits pet demographics and never sends health fields', async () => {
    const edited = { ...milo, breed: null, birthdate: null }
    const api = fakeClientApi({ profile, pets: [milo] })
    api.pets.mockResolvedValueOnce([milo]).mockResolvedValueOnce([edited])
    renderPanel(api)
    await waitForPanel()
    await openPetDetail()
    await userEvent.click(screen.getByRole('button', { name: 'Editar mascota' }))

    await userEvent.clear(screen.getByLabelText('Raza (opcional)'))
    fireEvent.change(screen.getByLabelText('Fecha de nacimiento (opcional)'), {
      target: { value: '' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Guardar mascota' }))

    await waitFor(() => {
      expect(api.updatePet).toHaveBeenCalledWith(milo.id, {
        name: 'Milo',
        species: 'Gato',
        breed: null,
        birthdate: null,
      })
    })
    expect(api.updatePet.mock.calls[0][1]).not.toHaveProperty('status')
    expect(api.updatePet.mock.calls[0][1]).not.toHaveProperty('health')
  })

  it('archives a pet without deleting reservation history', async () => {
    const pending = reservation({ id: 301, status: 'PENDING' })
    const api = fakeClientApi({
      profile,
      pets: [milo],
      reservations: [pending],
    })
    api.pets.mockResolvedValueOnce([milo]).mockResolvedValueOnce([])
    renderPanel(api)
    await waitForPanel()
    await openPetDetail()
    await userEvent.click(screen.getByRole('button', { name: 'Archivar mascota' }))

    expect(screen.getByText(/historial de reservas permanecerá/i)).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Sí, archivar' }))

    await waitFor(() => expect(api.archivePet).toHaveBeenCalledWith(milo.id))
    expect(await screen.findByText('No tienes mascotas activas registradas.')).toBeVisible()
    expect(screen.getByText('Consulta general')).toBeVisible()
    expect(api.reservations).toHaveBeenCalledTimes(1)
  })

  it('keeps the archive dialog open for the future-reservation conflict', async () => {
    const api = fakeClientApi({ profile, pets: [milo] })
    api.archivePet.mockRejectedValueOnce(new ApiError(
      'Cancela o resuelve primero las reservas futuras de esta mascota.',
      { code: 'PET_HAS_FUTURE_RESERVATION', status: 409 },
    ))
    renderPanel(api)
    await waitForPanel()
    await openPetDetail()
    await userEvent.click(screen.getByRole('button', { name: 'Archivar mascota' }))
    await userEvent.click(screen.getByRole('button', { name: 'Sí, archivar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/reservas futuras/i)
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(screen.getAllByText('Milo').length).toBeGreaterThan(0)
  })

  it('cancels once, normalizes the optional reason, and preserves the cancelled card', async () => {
    const pending = reservation({ id: 301, status: 'PENDING' })
    const cancelled = { ...pending, status: 'CANCELLED' }
    const api = fakeClientApi({ profile, pets: [milo], reservations: [pending] })
    api.reservations
      .mockResolvedValueOnce([pending])
      .mockResolvedValueOnce([cancelled])
    const cancellation = deferred()
    api.cancelReservation.mockReturnValueOnce(cancellation.promise)
    renderPanel(api)
    await waitForPanel()
    await userEvent.click(screen.getByRole('button', {
      name: 'Cancelar reserva de Milo',
    }))

    expect(screen.getByText(/historial permanecerá/i)).toBeVisible()
    await userEvent.type(screen.getByLabelText('Motivo (opcional)'), '  Cambio de planes  ')
    const confirm = screen.getByRole('button', { name: 'Confirmar cancelación' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    expect(api.cancelReservation).toHaveBeenCalledTimes(1)
    expect(api.cancelReservation).toHaveBeenCalledWith(301, 'Cambio de planes')
    expect(confirm).toBeDisabled()

    await act(async () => {
      cancellation.resolve({
        id: 301,
        status: 'CANCELLED',
        startsAt: pending.startsAt,
        cancelledAt: '2026-08-02T11:00:00-04:00',
        cancelledBy: 'CLIENT',
        reason: 'Cambio de planes',
        updatedAt: '2026-08-02T11:00:00-04:00',
      })
      await cancellation.promise
    })

    expect(await screen.findByText('Cancelada')).toBeVisible()
    expect(screen.getByText('Consulta general')).toBeVisible()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('offers cancellation only for future pending or confirmed reservations and keeps failures visible', async () => {
    const pastPending = reservation({
      id: 300,
      status: 'PENDING',
      startsAt: '2020-08-10T10:00:00-04:00',
    })
    const futureConfirmed = reservation({ id: 301, status: 'CONFIRMED' })
    const completed = reservation({ id: 302, status: 'COMPLETED' })
    const api = fakeClientApi({
      profile,
      pets: [milo],
      reservations: [pastPending, futureConfirmed, completed],
    })
    api.cancelReservation.mockRejectedValueOnce(new ApiError(
      'La reserva ya no puede cancelarse.',
      { code: 'RESERVATION_NOT_IN_FUTURE', status: 409 },
    ))
    renderPanel(api)
    await waitForPanel()

    const cancelButtons = screen.getAllByRole('button', { name: /cancelar reserva de/i })
    expect(cancelButtons).toHaveLength(1)
    await userEvent.click(cancelButtons[0])
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cancelación' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La reserva ya no puede cancelarse.',
    )
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(screen.getByText('Confirmada')).toBeVisible()
  })

  it('removes cancellation when an active reservation crosses its start time while the panel stays open', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-10T13:59:00Z'))
    try {
      const api = fakeClientApi({
        profile,
        pets: [milo],
        reservations: [reservation({
          id: 301,
          status: 'PENDING',
          startsAt: '2026-08-10T10:00:00-04:00',
        })],
      })
      renderPanel(api)

      await act(async () => {
        await Promise.resolve()
        await Promise.resolve()
      })
      expect(screen.getByRole('button', {
        name: 'Cancelar reserva de Milo',
      })).toBeVisible()

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2 * 60 * 1000)
      })

      expect(screen.queryByRole('button', {
        name: 'Cancelar reserva de Milo',
      })).not.toBeInTheDocument()
    } finally {
      vi.useRealTimers()
    }
  })

  it('uses the current time when reservations finish loading after their start boundary', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-10T13:59:00Z'))
    try {
      const reservationsRequest = deferred()
      const api = fakeClientApi({ profile, pets: [milo] })
      api.reservations.mockReturnValueOnce(reservationsRequest.promise)
      renderPanel(api)

      await act(async () => {
        await Promise.resolve()
        await Promise.resolve()
      })
      expect(screen.getByText('Cargando tu panel…')).toBeVisible()

      vi.setSystemTime(new Date('2026-08-10T14:01:00Z'))
      await act(async () => {
        reservationsRequest.resolve([reservation({
          id: 301,
          status: 'PENDING',
          startsAt: '2026-08-10T10:00:00-04:00',
        })])
        await reservationsRequest.promise
        await Promise.resolve()
      })

      expect(screen.queryByRole('button', {
        name: 'Cancelar reserva de Milo',
      })).not.toBeInTheDocument()
      const summary = screen.getByRole('region', {
        name: 'Resumen de la cuenta',
      })
      expect(within(summary).getByText('Sin horas próximas')).toBeVisible()
      expect(within(summary).queryByText('10:00 hrs')).not.toBeInTheDocument()
    } finally {
      vi.useRealTimers()
    }
  })

  it('navigates Agendar hora directly to the protected booking flow', async () => {
    const api = fakeClientApi({ profile })
    const { router } = renderPanel(api)
    await waitForPanel()

    await userEvent.click(screen.getByRole('button', { name: 'Agendar nueva hora' }))

    expect(await screen.findByRole('heading', { name: 'Reservar una hora' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/reservar')
  })

  it('shows notification count and association, then reads one without losing the list', async () => {
    const read = { ...unreadNotification, unread: false }
    const api = fakeClientApi({
      profile,
      notifications: [unreadNotification],
    })
    api.notifications
      .mockResolvedValueOnce([unreadNotification])
      .mockResolvedValueOnce([read])
    api.readNotification.mockResolvedValueOnce(read)
    renderPanel(api)
    await waitForPanel()

    await userEvent.click(screen.getByRole('button', {
      name: 'Notificaciones (1 sin leer)',
    }))
    expect(screen.getByText('Recordatorio de reserva')).toBeVisible()
    expect(screen.getByText('Tu reserva se acerca.')).toBeVisible()
    expect(screen.getByText('Reserva #301')).toBeVisible()
    expect(screen.getByText('02-08-2026 · 11:10')).toBeVisible()
    await userEvent.click(screen.getByRole('button', {
      name: 'Marcar como leída: Recordatorio de reserva',
    }))

    await waitFor(() => expect(api.readNotification).toHaveBeenCalledWith(901))
    expect(screen.getByRole('button', {
      name: 'Notificaciones (0 sin leer)',
    })).toBeVisible()
    expect(screen.getByText('Recordatorio de reserva')).toBeVisible()
  })

  it('handles read-all zero, failures, and duplicate submissions without dropping notifications', async () => {
    const request = deferred()
    const api = fakeClientApi({
      profile,
      notifications: [unreadNotification],
    })
    api.notifications
      .mockResolvedValueOnce([unreadNotification])
      .mockResolvedValueOnce([unreadNotification])
    api.readAllNotifications.mockReturnValueOnce(request.promise)
    renderPanel(api)
    await waitForPanel()
    await userEvent.click(screen.getByRole('button', {
      name: 'Notificaciones (1 sin leer)',
    }))

    const readAll = screen.getByRole('button', { name: 'Marcar todas como leídas' })
    fireEvent.click(readAll)
    fireEvent.click(readAll)
    expect(api.readAllNotifications).toHaveBeenCalledTimes(1)
    expect(readAll).toBeDisabled()

    await act(async () => {
      request.resolve({ markedRead: 0 })
      await request.promise
    })
    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(
        'No había notificaciones sin leer.',
      )
    })
    expect(screen.getByText('Recordatorio de reserva')).toBeVisible()

    api.readNotification.mockRejectedValueOnce(new ApiError(
      'No pudimos marcar la notificación.',
      { code: 'NOTIFICATION_UNAVAILABLE', status: 503 },
    ))
    await userEvent.click(screen.getByRole('button', {
      name: 'Marcar como leída: Recordatorio de reserva',
    }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos marcar la notificación.',
    )
    expect(screen.getByText('Recordatorio de reserva')).toBeVisible()
  })

  it('reschedules once using the exact returned offset start and reconciles server status', async () => {
    const confirmed = reservation({ id: 301, status: 'CONFIRMED' })
    const moved = {
      id: 301,
      previousStartsAt: confirmed.startsAt,
      startsAt: '2099-08-11T11:30:00-04:00',
      endsAt: '2099-08-11T12:00:00-04:00',
      previousStatus: 'CONFIRMED',
      status: 'PENDING',
      updatedAt: '2026-08-02T11:10:00-04:00',
    }
    const request = deferred()
    const api = fakeClientApi({ profile, pets: [milo], reservations: [confirmed] })
    api.rescheduleReservation.mockReturnValueOnce(request.promise)
    api.reservations
      .mockResolvedValueOnce([confirmed])
      .mockResolvedValueOnce([{ ...confirmed, ...moved }])
    const availabilityApi = {
      availability: vi.fn().mockResolvedValue([{
        startsAt: moved.startsAt,
        endsAt: moved.endsAt,
      }]),
    }
    renderPanelWithAvailability(api, availabilityApi)
    await waitForPanel()

    await userEvent.click(screen.getByRole('button', {
      name: 'Reprogramar reserva de Milo',
    }))
    await userEvent.click(screen.getByRole('button', { name: 'Buscar horarios' }))
    await userEvent.click(await screen.findByRole('radio', { name: /11:30/i }))
    const submit = screen.getByRole('button', { name: 'Guardar reprogramaciÃ³n' })
    fireEvent.click(submit)
    fireEvent.click(submit)

    expect(api.rescheduleReservation).toHaveBeenCalledTimes(1)
    expect(api.rescheduleReservation).toHaveBeenCalledWith(301, moved.startsAt)
    expect(submit).toBeDisabled()

    await act(async () => {
      request.resolve(moved)
      await request.promise
    })

    expect(await screen.findByText('Pendiente')).toBeVisible()
    expect(screen.getAllByText('11-08-2099').length).toBeGreaterThan(0)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('keeps rescheduling open, refreshes stale availability, and offers the new slots after 409', async () => {
    const confirmed = reservation({ id: 301, status: 'CONFIRMED' })
    const staleSlot = {
      startsAt: '2099-08-11T11:30:00-04:00',
      endsAt: '2099-08-11T12:00:00-04:00',
    }
    const freshSlot = {
      startsAt: '2099-08-11T12:30:00-04:00',
      endsAt: '2099-08-11T13:00:00-04:00',
    }
    const api = fakeClientApi({ profile, pets: [milo], reservations: [confirmed] })
    api.rescheduleReservation.mockRejectedValueOnce(new ApiError(
      'Ese horario acaba de ser reservado.',
      { code: 'SLOT_ALREADY_BOOKED', status: 409 },
    ))
    const availabilityApi = {
      availability: vi.fn()
        .mockResolvedValueOnce([staleSlot])
        .mockResolvedValueOnce([freshSlot]),
    }
    renderPanelWithAvailability(api, availabilityApi)
    await waitForPanel()

    await userEvent.click(screen.getByRole('button', {
      name: 'Reprogramar reserva de Milo',
    }))
    await userEvent.click(screen.getByRole('button', { name: 'Buscar horarios' }))
    await userEvent.click(await screen.findByRole('radio', { name: /11:30/i }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar reprogramaciÃ³n' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /horario ya no estÃ¡ disponible.*elige otro/i,
    )
    expect(availabilityApi.availability).toHaveBeenCalledTimes(2)
    expect(await screen.findByRole('radio', { name: /12:30/i })).toBeVisible()
    expect(screen.getByRole('dialog', { name: 'Reprogramar reserva' })).toBeVisible()
  })

  it('keeps a failed availability search retryable without issuing a write', async () => {
    const confirmed = reservation({ id: 301, status: 'CONFIRMED' })
    const availabilityApi = {
      availability: vi.fn()
        .mockRejectedValueOnce(new ApiError('No pudimos cargar los horarios.', {
          code: 'AVAILABILITY_UNAVAILABLE',
          status: 503,
        }))
        .mockResolvedValueOnce([{
          startsAt: '2099-08-11T11:30:00-04:00',
          endsAt: '2099-08-11T12:00:00-04:00',
        }]),
    }
    const api = fakeClientApi({ profile, pets: [milo], reservations: [confirmed] })
    renderPanelWithAvailability(api, availabilityApi)
    await waitForPanel()

    await userEvent.click(screen.getByRole('button', {
      name: 'Reprogramar reserva de Milo',
    }))
    await userEvent.click(screen.getByRole('button', { name: 'Buscar horarios' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos cargar los horarios.',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar horarios' }))

    expect(await screen.findByRole('radio', { name: /11:30/i })).toBeVisible()
    expect(availabilityApi.availability).toHaveBeenCalledTimes(2)
    expect(api.rescheduleReservation).not.toHaveBeenCalled()
  })

  it('offers rescheduling only for future pending or confirmed reservations', async () => {
    const api = fakeClientApi({
      profile,
      pets: [milo],
      reservations: [
        reservation({ id: 300, status: 'PENDING', startsAt: '2020-08-10T10:00:00-04:00' }),
        reservation({ id: 301, status: 'CONFIRMED' }),
        reservation({ id: 302, status: 'COMPLETED' }),
        reservation({ id: 303, status: 'CANCELLED' }),
      ],
    })
    renderPanel(api)
    await waitForPanel()

    expect(screen.getAllByRole('button', {
      name: /reprogramar reserva de/i,
    })).toHaveLength(1)
  })
})

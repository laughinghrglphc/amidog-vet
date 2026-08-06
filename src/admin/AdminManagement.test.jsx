import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { AuthContext } from '../auth/authContext'
import { renderApp } from '../test/renderApp'
import AdminDashboardPage from './AdminDashboardPage'

const reservationItem = {
  petId: 84,
  petName: 'Milo',
  species: 'Gato',
  breed: 'Mestizo',
  serviceId: 7,
  serviceName: 'Consulta general',
}

const secondReservationItem = {
  petId: 85,
  petName: 'Luna',
  species: 'Perro',
  breed: 'Quiltro',
  serviceId: 9,
  serviceName: 'Vacunación',
}

const pendingReservation = {
  id: 301,
  clientId: 12,
  clientName: 'Ana Pérez',
  clientEmail: 'ana@example.cl',
  clientPhone: '+56912345678',
  startsAt: '2026-08-10T10:00:00-04:00',
  endsAt: '2026-08-10T10:30:00-04:00',
  status: 'PENDING',
  clientNote: 'Control anual',
  createdAt: '2026-08-01T09:15:00-04:00',
  updatedAt: '2026-08-01T09:15:00-04:00',
  items: [reservationItem],
}

const reservationDetail = {
  ...pendingReservation,
  cancelledAt: null,
  cancelledBy: null,
  cancellationReason: null,
  events: [{
    id: 800,
    eventType: 'CREATED',
    actor: 'CLIENT',
    previousStatus: null,
    newStatus: 'PENDING',
    previousStartsAt: null,
    newStartsAt: '2026-08-10T10:00:00-04:00',
    reason: null,
    createdAt: '2026-08-01T09:15:00-04:00',
  }],
}

const dashboard = {
  profile: {
    name: 'Administradora AmiDog',
    email: 'admin@example.cl',
    role: 'ADMIN',
  },
  stats: { todayAppointments: 1, clients: 18, pets: 27 },
  appointments: [pendingReservation],
  services: [{ name: 'Consulta general', count: 1, percentage: 100 }],
  chart: {
    currentWeek: {
      label: 'Esta semana',
      labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
      values: [1, 0, 0, 0, 0, 0, 0],
    },
    previousWeek: {
      label: 'Semana pasada',
      labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
      values: [0, 0, 0, 0, 0, 0, 0],
    },
    currentMonth: {
      label: 'Este mes',
      labels: ['Sem 1', 'Sem 2', 'Sem 3', 'Sem 4', 'Sem 5'],
      values: [1, 0, 0, 0, 0],
    },
  },
  schedule: [{
    reservationId: 301,
    startsAt: pendingReservation.startsAt,
    endsAt: pendingReservation.endsAt,
    status: 'PENDING',
    items: [reservationItem],
  }],
}

const page = (content = []) => ({
  content,
  page: 0,
  size: 25,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
})

function resolved(value) {
  return vi.fn().mockResolvedValue(value)
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

const clientSummary = {
  id: 12,
  name: 'Ana Pérez',
  email: 'ana@example.cl',
  phone: '+56912345678',
  active: true,
  createdAt: '2025-01-02T10:00:00-03:00',
  updatedAt: '2026-07-01T10:00:00-04:00',
}

const clientDetail = {
  ...clientSummary,
  pets: [{
    id: 84,
    name: 'Milo',
    species: 'Gato',
    breed: 'Mestizo',
    birthdate: '2022-05-10',
    active: true,
  }],
  reservationCounts: {
    total: 3,
    upcoming: 1,
    pending: 1,
    confirmed: 0,
    cancelled: 1,
    completed: 1,
    noShow: 0,
  },
  upcomingReservations: [pendingReservation],
}

const petSummary = {
  id: 84,
  clientId: 12,
  ownerName: 'Ana Pérez',
  name: 'Milo',
  species: 'Gato',
  breed: 'Mestizo',
  birthdate: '2022-05-10',
  active: true,
  createdAt: '2022-05-10T10:00:00-04:00',
  updatedAt: '2026-07-01T10:00:00-04:00',
}

const petDetail = {
  ...petSummary,
  ownerEmail: 'ana@example.cl',
  recentReservations: [pendingReservation],
}

const services = [{
  id: 7,
  code: 'consulta-general',
  name: 'Consulta general',
  description: 'Evaluación general',
  active: true,
  displayOrder: 10,
}, {
  id: 8,
  code: 'vacunacion',
  name: 'Vacunación',
  description: null,
  active: false,
  displayOrder: 20,
}]

const weekly = [{
  id: 1,
  dayOfWeek: 1,
  start: '09:00:00',
  end: '13:00:00',
  active: true,
}, {
  id: 2,
  dayOfWeek: 1,
  start: '15:00:00',
  end: '18:00:00',
  active: true,
}, {
  id: 3,
  dayOfWeek: 2,
  start: '09:00:00',
  end: '12:00:00',
  active: false,
}]

const blocks = [{
  id: 90,
  startsAt: '2026-08-15T00:00:00-04:00',
  endsAt: '2026-08-16T00:00:00-04:00',
  reason: 'Cirugía externa',
  createdAt: '2026-08-01T09:00:00-04:00',
}]

const notifications = [{
  id: 901,
  type: 'NEW_RESERVATION',
  title: 'Nueva reserva',
  body: 'Ana reservó para Milo.',
  reservationId: 301,
  createdAt: '2026-08-02T15:10:00Z',
  unread: true,
}]

function managementApi(overrides = {}) {
  const loadDashboard = resolved(dashboard)
  return {
    dashboard: loadDashboard,
    reservations: resolved(page([pendingReservation])),
    reservation: resolved(reservationDetail),
    changeStatus: resolved({
      id: 301,
      previousStatus: 'PENDING',
      status: 'CONFIRMED',
      reason: null,
      updatedAt: '2026-08-02T10:00:00-04:00',
    }),
    reschedule: resolved(undefined),
    clients: resolved(page([clientSummary])),
    client: resolved(clientDetail),
    updateClient: resolved(undefined),
    pets: resolved(page([petSummary])),
    pet: resolved(petDetail),
    updatePet: resolved(undefined),
    services: resolved(services),
    createService: resolved(undefined),
    updateService: resolved(undefined),
    archiveService: resolved(undefined),
    weeklyAvailability: resolved(weekly),
    replaceWeeklyAvailability: resolved(weekly),
    blocks: resolved(blocks),
    createBlock: resolved(undefined),
    deleteBlock: resolved(undefined),
    notifications: resolved(notifications),
    readNotification: resolved(undefined),
    readAllNotifications: resolved({ markedRead: 1 }),
    ...overrides,
  }
}

function renderManagement(api, availabilityApi = {
  availability: resolved([{
    startsAt: '2026-08-11T11:30:00-04:00',
    endsAt: '2026-08-11T12:00:00-04:00',
  }]),
}) {
  const auth = {
    error: null,
    loading: false,
    login: vi.fn(),
    logout: resolved(true),
    refresh: vi.fn(),
    retry: vi.fn(),
    user: {
      userId: 1,
      clientId: null,
      email: 'admin@example.cl',
      name: 'Administradora AmiDog',
      accountType: 'ADMIN',
    },
  }

  return renderApp(
    <Routes>
      <Route
        path="/admin/*"
        element={
          <AuthContext.Provider value={auth}>
            <AdminDashboardPage
              api={api}
              availabilityApi={availabilityApi}
              now={new Date('2026-08-02T12:00:00-04:00')}
            />
          </AuthContext.Provider>
        }
      />
    </Routes>,
    { route: '/admin' },
  )
}

describe('administrator management', () => {
  it('confirms a pending reservation and keeps its event history visible', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)

    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Reservas' }))
    const list = await screen.findByRole('dialog', { name: /reservas/i })
    await user.click(within(list).getByRole('button', { name: /Milo/i }))

    const detail = await screen.findByRole('dialog', { name: /detalle de la reserva/i })
    const history = within(detail).getByRole('heading', { name: 'Historial' })
      .closest('section')
    expect(within(history).getByText('Creada')).toBeVisible()
    await user.click(within(detail).getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByRole('status')).toHaveTextContent(/confirmada/i)
    expect(api.changeStatus).toHaveBeenCalledWith(301, {
      status: 'CONFIRMED',
      reason: '',
    })
    expect(api.reservation).toHaveBeenCalledTimes(2)
    expect(api.reservations).toHaveBeenCalledTimes(2)
    expect(api.dashboard).toHaveBeenCalledTimes(2)
  })

  it.each([
    ['PENDING', ['Confirmar', 'Cancelar', 'Reprogramar'], ['Completar', 'Marcar como inasistencia']],
    ['CONFIRMED', ['Cancelar', 'Reprogramar', 'Completar', 'Marcar como inasistencia'], ['Confirmar']],
    ['CANCELLED', [], ['Confirmar', 'Cancelar', 'Reprogramar', 'Completar', 'Marcar como inasistencia']],
    ['COMPLETED', [], ['Confirmar', 'Cancelar', 'Reprogramar', 'Completar', 'Marcar como inasistencia']],
    ['NO_SHOW', [], ['Confirmar', 'Cancelar', 'Reprogramar', 'Completar', 'Marcar como inasistencia']],
  ])('shows only valid lifecycle actions for %s', async (status, visible, hidden) => {
    const summary = { ...pendingReservation, status }
    const api = managementApi({
      reservations: resolved(page([summary])),
      reservation: resolved({ ...reservationDetail, status }),
    })
    const user = userEvent.setup()
    renderManagement(api)

    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Reservas' }))
    await user.click(within(await screen.findByRole('dialog', { name: /reservas/i }))
      .getByRole('button', { name: /Milo/i }))
    const detail = await screen.findByRole('dialog', { name: /detalle de la reserva/i })

    visible.forEach((name) => {
      expect(within(detail).getByRole('button', { name })).toBeVisible()
    })
    hidden.forEach((name) => {
      expect(within(detail).queryByRole('button', { name })).not.toBeInTheDocument()
    })
    expect(within(detail).queryByRole('button', { name: /eliminar reserva/i }))
      .not.toBeInTheDocument()
  })

  it('cancels with an optional bounded reason and explicitly preserves history', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)

    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Reservas' }))
    await user.click(within(await screen.findByRole('dialog', { name: /reservas/i }))
      .getByRole('button', { name: /Milo/i }))
    await user.click(within(await screen.findByRole('dialog', { name: /detalle/i }))
      .getByRole('button', { name: 'Cancelar' }))

    const reason = screen.getByLabelText('Motivo de cancelación (opcional)')
    expect(reason).toHaveAttribute('maxlength', '300')
    expect(screen.getByText(/la reserva y su historial se conservarán/i)).toBeVisible()
    await user.type(reason, 'Agenda de la clínica')
    await user.click(screen.getByRole('button', { name: 'Confirmar cancelación' }))

    expect(api.changeStatus).toHaveBeenCalledWith(301, {
      status: 'CANCELLED',
      reason: 'Agenda de la clínica',
    })
  })

  it('reschedules with an exact public server slot and preserves a 409 correction', async () => {
    const error = new ApiError('Ese horario ya no está disponible.', {
      code: 'SLOT_UNAVAILABLE',
      status: 409,
    })
    const api = managementApi({ reschedule: vi.fn().mockRejectedValue(error) })
    const availabilityApi = {
      availability: resolved([{
        startsAt: '2026-08-11T11:30:00-04:00',
        endsAt: '2026-08-11T12:00:00-04:00',
      }]),
    }
    const user = userEvent.setup()
    renderManagement(api, availabilityApi)

    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Reservas' }))
    await user.click(within(await screen.findByRole('dialog', { name: /reservas/i }))
      .getByRole('button', { name: /Milo/i }))
    await user.click(within(await screen.findByRole('dialog', { name: /detalle/i }))
      .getByRole('button', { name: 'Reprogramar' }))
    await user.clear(screen.getByLabelText('Desde'))
    await user.type(screen.getByLabelText('Desde'), '2026-08-11')
    await user.clear(screen.getByLabelText('Hasta'))
    await user.type(screen.getByLabelText('Hasta'), '2026-08-11')
    await user.click(screen.getByRole('button', { name: 'Buscar horarios' }))
    await user.click(await screen.findByRole('radio', { name: /11:30/i }))
    await user.click(screen.getByRole('button', { name: 'Guardar reprogramación' }))

    expect(api.reschedule).toHaveBeenCalledWith(301, '2026-08-11T11:30:00-04:00')
    expect(await screen.findByRole('alert')).toHaveTextContent(/elige otro horario/i)
    expect(screen.getByRole('button', { name: 'Guardar reprogramación' })).toBeVisible()
  })

  it('creates, edits, archives, and reactivates services while keeping codes immutable', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Servicios' }))
    await screen.findByText('Vacunación')

    await user.click(screen.getByRole('button', { name: 'Crear servicio' }))
    await user.type(screen.getByLabelText('Código'), 'peluqueria')
    await user.type(screen.getByLabelText('Nombre'), 'Peluquería')
    await user.type(screen.getByLabelText('Orden'), '30')
    await user.click(screen.getByRole('button', { name: 'Guardar servicio' }))
    expect(api.createService).toHaveBeenCalledWith({
      code: 'peluqueria',
      name: 'Peluquería',
      description: null,
      displayOrder: 30,
    })

    await user.click(screen.getByRole('button', { name: 'Editar Consulta general' }))
    expect(screen.getByLabelText('Código')).toHaveAttribute('readonly')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(api.updateService).toHaveBeenCalledWith(7, {
      name: 'Consulta general',
      description: 'Evaluación general',
      displayOrder: 10,
      active: true,
    })
    await user.click(screen.getByRole('button', { name: 'Archivar Consulta general' }))
    expect(api.archiveService).toHaveBeenCalledWith(7)
    await user.click(screen.getByRole('button', { name: 'Reactivar Vacunación' }))
    expect(api.updateService).toHaveBeenCalledWith(8, {
      name: 'Vacunación',
      description: null,
      displayOrder: 20,
      active: true,
    })
    expect(api.dashboard).toHaveBeenCalledTimes(1)
  })

  it('replaces all weekly intervals without discarding inactive intervals', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Disponibilidad' }))
    expect(await screen.findByLabelText('Inicio Lunes 1')).toBeVisible()
    expect(screen.getByLabelText('Inicio Lunes 2')).toBeVisible()
    expect(screen.getByLabelText('Inicio Martes 3')).toBeVisible()

    await user.selectOptions(screen.getByLabelText('Día del nuevo intervalo'), '3')
    await user.click(screen.getByRole('button', { name: 'Agregar intervalo' }))
    await user.click(screen.getByRole('button', { name: 'Guardar horario semanal' }))

    expect(api.replaceWeeklyAvailability).toHaveBeenCalledWith([
      { dayOfWeek: 1, start: '09:00:00', end: '13:00:00', active: true },
      { dayOfWeek: 1, start: '15:00:00', end: '18:00:00', active: true },
      { dayOfWeek: 2, start: '09:00:00', end: '12:00:00', active: false },
      { dayOfWeek: 3, start: '09:00:00', end: '17:00:00', active: true },
    ])
    expect(api.dashboard).toHaveBeenCalledTimes(1)
  })

  it('creates partial and full-day blocks with Chile offsets and keeps typed conflicts visible', async () => {
    const conflict = new ApiError('El bloqueo se superpone con reservas activas.', {
      code: 'BLOCK_OVERLAPS_RESERVATIONS',
      details: { reservationIds: [301, 302] },
      status: 409,
    })
    const createBlock = vi.fn()
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(conflict)
    const api = managementApi({ createBlock })
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Disponibilidad' }))
    await screen.findByText('Cirugía externa')

    await user.type(screen.getByLabelText('Inicio del bloqueo'), '2026-08-10T10:00')
    await user.type(screen.getByLabelText('Fin del bloqueo'), '2026-08-10T12:00')
    await user.click(screen.getByRole('button', { name: 'Crear bloqueo' }))
    expect(createBlock).toHaveBeenNthCalledWith(1, {
      startsAt: '2026-08-10T10:00:00-04:00',
      endsAt: '2026-08-10T12:00:00-04:00',
      reason: null,
    })

    await user.click(screen.getByLabelText('Día completo'))
    await user.type(screen.getByLabelText('Fecha del bloqueo'), '2026-01-10')
    await user.click(screen.getByRole('button', { name: 'Crear bloqueo' }))
    expect(createBlock).toHaveBeenNthCalledWith(2, {
      startsAt: '2026-01-10T00:00:00-03:00',
      endsAt: '2026-01-11T00:00:00-03:00',
      reason: null,
    })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/reservas activas/i)
    expect(alert).toHaveTextContent(/301, 302/)
    expect(screen.getByRole('button', { name: 'Crear bloqueo' })).toBeVisible()
    expect(api.dashboard).toHaveBeenCalledTimes(1)
  })

  it('filters, paginates, opens, and updates clients using only editable safe fields', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Clientes' }))
    const clientsDialog = screen.getByRole('dialog', { name: 'Clientes' })
    await within(clientsDialog).findByRole('button', { name: /Ana Pérez/i })
    await user.type(screen.getByLabelText('Buscar clientes'), 'ana')
    await user.selectOptions(screen.getByLabelText('Estado del cliente'), 'true')
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(api.clients).toHaveBeenLastCalledWith('?q=ana&active=true&page=0&size=25')

    await user.click(within(clientsDialog).getByRole('button', { name: /Ana Pérez/i }))
    expect(await screen.findByDisplayValue('ana@example.cl')).toHaveAttribute('readonly')
    expect(screen.getByText(/3 reservas/i)).toBeVisible()
    await user.clear(screen.getByLabelText('Teléfono'))
    await user.type(screen.getByLabelText('Teléfono'), '+56911111111')
    await user.click(screen.getByRole('button', { name: 'Guardar cliente' }))
    expect(api.updateClient).toHaveBeenCalledWith(12, {
      name: 'Ana Pérez',
      phone: '+56911111111',
      active: true,
    })
    expect(screen.queryByLabelText(/identificador|historial editable/i)).not.toBeInTheDocument()
  })

  it('updates only pet demographics and keeps owner/archive/history read-only', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Mascotas' }))
    const petsDialog = screen.getByRole('dialog', { name: 'Mascotas' })
    await user.click(await within(petsDialog).findByRole('button', { name: /Milo/i }))
    expect(await screen.findByDisplayValue('Ana Pérez')).toHaveAttribute('readonly')
    expect(screen.getByText(/reserva reciente/i)).toBeVisible()
    await user.clear(screen.getByLabelText('Raza'))
    await user.click(screen.getByRole('button', { name: 'Guardar mascota' }))
    expect(api.updatePet).toHaveBeenCalledWith(84, {
      name: 'Milo',
      species: 'Gato',
      breed: null,
      birthdate: '2022-05-10',
    })
    expect(screen.queryByText(/salud|médic|clínic/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /archivar mascota/i })).not.toBeInTheDocument()
  })

  it('marks operational notifications individually and all at once without messages or replies', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Notificaciones' }))
    expect(await screen.findByText('Nueva reserva')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Marcar Nueva reserva como leída' }))
    expect(api.readNotification).toHaveBeenCalledWith(901)
    await user.click(screen.getByRole('button', { name: 'Marcar todas como leídas' }))
    expect(api.readAllNotifications).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('textbox', { name: /respuesta|mensaje/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /whatsapp|responder/i })).not.toBeInTheDocument()
  })

  it('keeps reservation detail, truthful data, status, and final focus through a deferred dashboard refresh', async () => {
    const dashboardRefresh = deferred()
    const confirmedDetail = {
      ...reservationDetail,
      status: 'CONFIRMED',
      updatedAt: '2026-08-02T10:00:00-04:00',
      events: [...reservationDetail.events, {
        id: 801,
        eventType: 'STATUS_CHANGED',
        actor: 'ADMIN',
        previousStatus: 'PENDING',
        newStatus: 'CONFIRMED',
        previousStartsAt: null,
        newStartsAt: null,
        reason: null,
        createdAt: '2026-08-02T10:00:00-04:00',
      }],
    }
    const api = managementApi({
      dashboard: vi.fn()
        .mockResolvedValueOnce(dashboard)
        .mockReturnValueOnce(dashboardRefresh.promise),
      reservation: vi.fn()
        .mockResolvedValueOnce(reservationDetail)
        .mockResolvedValueOnce(confirmedDetail),
      reservations: vi.fn()
        .mockResolvedValueOnce(page([pendingReservation]))
        .mockResolvedValueOnce(page([{ ...pendingReservation, status: 'CONFIRMED' }])),
    })
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    const trigger = screen.getByRole('button', { name: 'Reservas' })
    await user.click(trigger)
    await user.click(within(await screen.findByRole('dialog', { name: 'Reservas' }))
      .getByRole('button', { name: /Milo/i }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Detalle de la reserva' }))
      .getByRole('button', { name: 'Confirmar' }))

    const pendingDialog = screen.getByRole('dialog', { name: 'Detalle de la reserva' })
    expect(pendingDialog).toBeVisible()
    expect(within(pendingDialog).getByText('Confirmada')).toBeVisible()

    await act(async () => {
      dashboardRefresh.resolve({
        ...dashboard,
        appointments: [{ ...pendingReservation, status: 'CONFIRMED' }],
      })
      await dashboardRefresh.promise
    })

    const refreshedDialog = screen.getByRole('dialog', { name: 'Detalle de la reserva' })
    expect(within(refreshedDialog).getByRole('status')).toHaveTextContent('confirmada')
    await user.click(within(refreshedDialog).getByRole('button', { name: 'Cerrar' }))
    expect(trigger).toHaveFocus()
  })

  it.each([
    {
      nav: 'Clientes',
      listName: /Ana Pérez/i,
      field: 'Teléfono',
      nextValue: '+56911111111',
      save: 'Guardar cliente',
      detailMethod: 'client',
      updateMethod: 'updateClient',
      initialDetail: clientDetail,
      updatedDetail: { ...clientDetail, phone: '+56911111111' },
      expectedStatus: /cliente actualizado/i,
    },
    {
      nav: 'Mascotas',
      listName: /Milo/i,
      field: 'Raza',
      nextValue: 'Europeo',
      save: 'Guardar mascota',
      detailMethod: 'pet',
      updateMethod: 'updatePet',
      initialDetail: petDetail,
      updatedDetail: { ...petDetail, breed: 'Europeo' },
      expectedStatus: /mascota actualizada/i,
    },
  ])('keeps $nav detail and status mounted through a deferred dashboard refresh', async ({
    nav,
    listName,
    field,
    nextValue,
    save,
    detailMethod,
    updateMethod,
    initialDetail,
    updatedDetail,
    expectedStatus,
  }) => {
    const dashboardRefresh = deferred()
    const api = managementApi({
      dashboard: vi.fn()
        .mockResolvedValueOnce(dashboard)
        .mockReturnValueOnce(dashboardRefresh.promise),
      [detailMethod]: vi.fn()
        .mockResolvedValueOnce(initialDetail)
        .mockResolvedValueOnce(updatedDetail),
      [updateMethod]: resolved(undefined),
    })
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    const trigger = screen.getByRole('button', { name: nav })
    await user.click(trigger)
    const dialog = await screen.findByRole('dialog', { name: nav })
    await user.click(await within(dialog).findByRole('button', { name: listName }))
    const fieldInput = await screen.findByLabelText(field)
    await user.clear(fieldInput)
    await user.type(fieldInput, nextValue)
    await user.click(screen.getByRole('button', { name: save }))

    expect(screen.getByRole('dialog', { name: nav })).toBeVisible()
    expect(screen.getByDisplayValue(nextValue)).toBeVisible()

    await act(async () => {
      dashboardRefresh.resolve(dashboard)
      await dashboardRefresh.promise
    })

    expect(screen.getByRole('status')).toHaveTextContent(expectedStatus)
    await user.click(within(screen.getByRole('dialog', { name: nav }))
      .getByRole('button', { name: 'Cerrar' }))
    expect(trigger).toHaveFocus()
  })

  it('renders complete multi-pet reservation and client history DTO fields in clinic time', async () => {
    const richSummary = {
      ...pendingReservation,
      startsAt: '2026-04-04T23:00:00-03:00',
      endsAt: '2026-04-04T23:30:00-03:00',
      status: 'CANCELLED',
      clientNote: 'Atender juntas',
      updatedAt: '2026-04-03T12:00:00-03:00',
      items: [reservationItem, secondReservationItem],
    }
    const richDetail = {
      ...richSummary,
      cancelledAt: '2026-04-03T12:00:00-03:00',
      cancelledBy: 'ADMIN',
      cancellationReason: 'Cierre extraordinario',
      events: [{
        id: 810,
        eventType: 'RESCHEDULED',
        actor: 'ADMIN',
        previousStatus: 'PENDING',
        newStatus: 'PENDING',
        previousStartsAt: '2026-04-03T23:00:00-03:00',
        newStartsAt: '2026-04-04T23:00:00-03:00',
        reason: 'Solicitud del cliente',
        createdAt: '2026-04-02T11:00:00-03:00',
      }, {
        id: 811,
        eventType: 'CANCELLED',
        actor: 'ADMIN',
        previousStatus: 'PENDING',
        newStatus: 'CANCELLED',
        previousStartsAt: null,
        newStartsAt: null,
        reason: 'Cierre extraordinario',
        createdAt: '2026-04-03T12:00:00-03:00',
      }],
    }
    const richClient = {
      ...clientDetail,
      reservationCounts: {
        total: 7,
        upcoming: 1,
        pending: 1,
        confirmed: 1,
        cancelled: 2,
        completed: 1,
        noShow: 2,
      },
      upcomingReservations: [{ ...richSummary, status: 'PENDING' }],
    }
    const api = managementApi({
      reservations: resolved(page([richSummary])),
      reservation: resolved(richDetail),
      client: resolved(richClient),
    })
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Reservas' }))
    const list = await screen.findByRole('dialog', { name: 'Reservas' })
    const row = within(list).getByRole('button', { name: /Milo/i })
    expect(row).toHaveAccessibleName(/Milo.*Consulta general.*Luna.*Vacunación/i)
    expect(row).toHaveTextContent('ana@example.cl')
    expect(row).toHaveTextContent('Atender juntas')
    expect(row).toHaveTextContent(/Inicio|Fin|Creada|Actualizada/)
    await user.click(row)

    const detail = await screen.findByRole('dialog', { name: 'Detalle de la reserva' })
    expect(detail).toHaveTextContent(/Milo.*Gato.*Mestizo.*Consulta general/i)
    expect(detail).toHaveTextContent(/Luna.*Perro.*Quiltro.*Vacunación/i)
    expect(detail).toHaveTextContent(/Cancelada por.*Administración/i)
    expect(detail).toHaveTextContent(/Cancelada el/i)
    expect(detail).toHaveTextContent(/Pendiente.*Cancelada/i)
    expect(detail).toHaveTextContent(/Horario anterior.*Horario nuevo/i)
    expect(detail).toHaveTextContent(/Solicitud del cliente/)
    expect(detail).toHaveTextContent(/Fecha del evento/i)
    await user.click(within(detail).getByRole('button', { name: 'Cerrar' }))

    await user.click(screen.getByRole('button', { name: 'Clientes' }))
    const clientsDialog = await screen.findByRole('dialog', { name: 'Clientes' })
    await user.click(within(clientsDialog).getByRole('button', { name: /Ana Pérez/i }))
    expect(await screen.findByText(/Canceladas: 2/i)).toBeVisible()
    expect(screen.getByText(/Inasistencias: 2/i)).toBeVisible()
    expect(within(clientsDialog).getByText(/Próximas reservas/i)).toBeVisible()
    expect(screen.getByText(/Milo.*Consulta general.*Luna.*Vacunación/i)).toBeVisible()
  })

  it('does not reload or update management state after a pending mutation unmounts', async () => {
    const completion = deferred()
    const api = managementApi({
      changeStatus: vi.fn().mockReturnValue(completion.promise),
    })
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Reservas' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Reservas' }))
      .getByRole('button', { name: /Milo/i }))
    const detail = await screen.findByRole('dialog', { name: 'Detalle de la reserva' })
    await user.click(within(detail).getByRole('button', { name: 'Confirmar' }))
    await user.click(within(detail).getByRole('button', { name: 'Cerrar' }))

    await act(async () => {
      completion.resolve({
        id: 301,
        previousStatus: 'PENDING',
        status: 'CONFIRMED',
        reason: null,
        updatedAt: '2026-08-02T10:00:00-04:00',
      })
      await completion.promise
    })

    expect(api.reservations).toHaveBeenCalledTimes(1)
    expect(api.reservation).toHaveBeenCalledTimes(1)
    expect(api.dashboard).toHaveBeenCalledTimes(1)
  })

  it('rejects out-of-contract reservation dates and reversed ranges before querying', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Reservas' }))
    await screen.findByRole('dialog', { name: 'Reservas' })
    const initialCalls = api.reservations.mock.calls.length

    await user.type(screen.getByLabelText('Desde'), '1899-12-31')
    await user.type(screen.getByLabelText('Hasta'), '2101-01-01')
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(screen.getByRole('alert')).toHaveTextContent(/1900.*2100/i)
    expect(api.reservations).toHaveBeenCalledTimes(initialCalls)

    await user.clear(screen.getByLabelText('Desde'))
    await user.type(screen.getByLabelText('Desde'), '2026-08-20')
    await user.clear(screen.getByLabelText('Hasta'))
    await user.type(screen.getByLabelText('Hasta'), '2026-08-10')
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(screen.getByRole('alert')).toHaveTextContent(/desde.*anterior.*hasta/i)
    expect(api.reservations).toHaveBeenCalledTimes(initialCalls)
  })

  it('rejects invalid and overlapping active weekly intervals without mutation', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Disponibilidad' }))
    await screen.findByLabelText('Inicio Lunes 1')

    await user.clear(screen.getByLabelText('Fin Lunes 1'))
    await user.type(screen.getByLabelText('Fin Lunes 1'), '08:00')
    await user.click(screen.getByRole('button', { name: 'Guardar horario semanal' }))
    expect(screen.getByRole('alert')).toHaveTextContent(/inicio.*anterior.*fin/i)
    expect(api.replaceWeeklyAvailability).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('Fin Lunes 1'))
    await user.type(screen.getByLabelText('Fin Lunes 1'), '16:00')
    await user.click(screen.getByRole('button', { name: 'Guardar horario semanal' }))
    expect(screen.getByRole('alert')).toHaveTextContent(/intervalos activos.*superpon/i)
    expect(api.replaceWeeklyAvailability).not.toHaveBeenCalled()
  })

  it('rejects a reversed partial block with a field-level correction and no POST', async () => {
    const api = managementApi()
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Disponibilidad' }))
    await screen.findByText(/Cirug.*externa/i)

    await user.type(screen.getByLabelText('Inicio del bloqueo'), '2026-08-10T12:00')
    await user.type(screen.getByLabelText('Fin del bloqueo'), '2026-08-10T10:00')
    await user.click(screen.getByRole('button', { name: 'Crear bloqueo' }))

    expect(screen.getByRole('alert')).toHaveTextContent(/fin.*posterior.*inicio/i)
    expect(screen.getByLabelText('Fin del bloqueo')).toHaveAttribute('aria-invalid', 'true')
    expect(api.createBlock).not.toHaveBeenCalled()
  })

  it('reloads server truth after a stale status conflict while keeping the correction visible', async () => {
    const confirmedDetail = { ...reservationDetail, status: 'CONFIRMED' }
    const api = managementApi({
      changeStatus: vi.fn().mockRejectedValueOnce(new ApiError(
        'TransiciÃ³n obsoleta.',
        { code: 'INVALID_RESERVATION_STATUS_TRANSITION', status: 409 },
      )),
      reservation: vi.fn()
        .mockResolvedValueOnce(reservationDetail)
        .mockResolvedValueOnce(confirmedDetail),
      reservations: vi.fn()
        .mockResolvedValueOnce(page([pendingReservation]))
        .mockResolvedValueOnce(page([{ ...pendingReservation, status: 'CONFIRMED' }])),
    })
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Reservas' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Reservas' }))
      .getByRole('button', { name: /Milo/i }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Detalle de la reserva' }))
      .getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /estado cambi.*detalle actualizado/i,
    )
    const detail = screen.getByRole('dialog', { name: 'Detalle de la reserva' })
    expect(within(detail).queryByRole('button', { name: 'Confirmar' })).not.toBeInTheDocument()
    expect(within(detail).getByRole('button', { name: 'Completar' })).toBeVisible()
    expect(api.reservation).toHaveBeenCalledTimes(2)
    expect(api.reservations).toHaveBeenCalledTimes(2)
    expect(api.dashboard).toHaveBeenCalledTimes(2)
  })

  it('hides known-stale actions and offers a distinct retry when conflict refresh fails', async () => {
    const confirmedDetail = { ...reservationDetail, status: 'CONFIRMED' }
    const api = managementApi({
      changeStatus: vi.fn().mockRejectedValueOnce(new ApiError(
        'TransiciÃ³n obsoleta.',
        { code: 'INVALID_RESERVATION_STATUS_TRANSITION', status: 409 },
      )),
      reservation: vi.fn()
        .mockResolvedValueOnce(reservationDetail)
        .mockRejectedValueOnce(new Error('Sin conexiÃ³n'))
        .mockResolvedValueOnce(confirmedDetail),
      reservations: vi.fn()
        .mockResolvedValueOnce(page([pendingReservation]))
        .mockResolvedValue(page([{ ...pendingReservation, status: 'CONFIRMED' }])),
    })
    const user = userEvent.setup()
    renderManagement(api)
    await screen.findByRole('heading', { name: /Bienvenida/i })
    await user.click(screen.getByRole('button', { name: 'Reservas' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Reservas' }))
      .getByRole('button', { name: /Milo/i }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Detalle de la reserva' }))
      .getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /estado cambi.*no pudimos actualizar/i,
    )
    expect(screen.queryByRole('button', { name: 'Confirmar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Completar' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reintentar detalle actualizado' }))
    expect(await screen.findByRole('button', { name: 'Completar' })).toBeVisible()
    expect(api.changeStatus).toHaveBeenCalledTimes(1)
  })
})

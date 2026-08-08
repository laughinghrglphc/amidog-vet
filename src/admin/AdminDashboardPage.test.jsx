import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { AuthContext } from '../auth/authContext'
import { fakeAdminApi } from '../test/fakes'
import { renderApp } from '../test/renderApp'
import AdminDashboardPage from './AdminDashboardPage'
import { imageKeyForSpecies } from './utils/petImage'

const now = new Date('2026-07-28T12:00:00-04:00')

function deferred() {
  let reject
  let resolve
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

const dashboard = {
  profile: {
    name: 'Nataly Apablaza',
    email: 'nataly@amidog.cl',
    role: 'ADMIN',
  },
  stats: {
    todayAppointments: 1,
    clients: 18,
    pets: 27,
  },
  appointments: [
    {
      id: 42,
      clientId: 7,
      clientName: 'María González',
      clientEmail: 'maria@example.cl',
      clientPhone: '+56912345678',
      startsAt: '2026-07-28T15:30:00-04:00',
      endsAt: '2026-07-28T16:00:00-04:00',
      status: 'CONFIRMED',
      clientNote: 'Control anual',
      createdAt: '2026-07-20T09:00:00-04:00',
      updatedAt: '2026-07-21T10:00:00-04:00',
      items: [
        {
          petId: 11,
          petName: 'Moka',
          species: 'Gato doméstico',
          breed: 'Calicó',
          serviceId: 3,
          serviceName: 'Consulta general',
        },
      ],
    },
  ],
  services: [
    {
      name: 'Consulta general',
      count: 12,
      percentage: 75,
    },
  ],
  chart: {
    currentWeek: {
      label: 'Esta semana',
      labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
      values: [1, 2, 3, 4, 5, 6, 7],
    },
    previousWeek: {
      label: 'Semana pasada',
      labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
      values: [0, 1, 1, 2, 3, 5, 8],
    },
    currentMonth: {
      label: 'Este mes',
      labels: ['Sem 1', 'Sem 2', 'Sem 3', 'Sem 4', 'Sem 5'],
      values: [4, 8, 12, 16, 20],
    },
  },
  schedule: [
    {
      reservationId: 42,
      startsAt: '2026-07-28T15:30:00-04:00',
      endsAt: '2026-07-28T16:00:00-04:00',
      status: 'CONFIRMED',
      items: [
        {
          petId: 11,
          petName: 'Moka',
          species: 'Gato doméstico',
          breed: 'Calicó',
          serviceId: 3,
          serviceName: 'Consulta general',
        },
      ],
    },
  ],
}

function authValue(logout = vi.fn().mockResolvedValue(true)) {
  return {
    error: null,
    loading: false,
    login: vi.fn(),
    logout,
    refresh: vi.fn(),
    retry: vi.fn(),
    user: {
      userId: 1,
      clientId: null,
      email: 'nataly@amidog.cl',
      name: 'Nataly Apablaza',
      accountType: 'ADMIN',
    },
  }
}

function AuthWrapper({ children, value }) {
  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

function renderDashboard({
  api,
  currentDashboard = dashboard,
  currentNow = now,
  logout,
} = {}) {
  const dashboardApi = api ?? fakeAdminApi({ dashboard: currentDashboard })
  const value = authValue(logout)
  const result = renderApp(
    <Routes>
      <Route
        path="/admin/*"
        element={<AdminDashboardPage api={dashboardApi} now={currentNow} />}
      />
      <Route path="/login" element={<h1>Iniciar sesión</h1>} />
      <Route path="/siguiente" element={<h1>Página siguiente</h1>} />
    </Routes>,
    {
      route: '/admin',
      Wrapper: AuthWrapper,
      wrapperProps: { value },
    },
  )

  return { ...result, auth: value }
}

describe('AdminDashboardPage', () => {
  it('loads the complete dashboard API response and exposes no messages navigation', async () => {
    const api = fakeAdminApi({ dashboard })
    renderDashboard({ api })

    expect(await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })).toBeVisible()
    expect(screen.getByText('Martes, 28 de julio')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Próximas reservas' })).toBeVisible()
    expect(screen.getAllByText('Moka')[0]).toBeVisible()
    expect(screen.getByText('María González')).toBeVisible()
    expect(api.dashboard).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: /mensajes/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /notificaciones/i })).toBeVisible()
    expect(screen.queryByRole('button', { name: /configuración/i })).not.toBeInTheDocument()
  })

  it('keeps the unread badge until the administrator explicitly marks a notification as read', async () => {
    const unread = {
      id: 901,
      type: 'NEW_RESERVATION',
      title: 'Nueva reserva',
      body: 'Se registró una nueva reserva para Milo.',
      reservationId: 42,
      createdAt: '2026-08-05T13:15:00Z',
      unread: true,
    }
    let notifications = [unread]
    const api = fakeAdminApi({ dashboard })
    api.notifications.mockImplementation(async () => notifications)
    api.readNotification.mockImplementation(async (id) => {
      const saved = { ...unread, id, unread: false }
      notifications = [saved]
      return saved
    })
    const user = userEvent.setup()

    renderDashboard({ api })

    await waitFor(() => {
      expect(api.notifications).toHaveBeenCalledTimes(1)
    })

    const notificationButton = await screen.findByRole('button', {
      name: 'Notificaciones, 1 sin leer',
    })
    await user.click(notificationButton)

    const dialog = screen.getByRole('dialog', {
      name: 'Notificaciones operacionales',
    })
    expect(within(dialog).getByText('Se registró una nueva reserva para Milo.'))
      .toBeVisible()
    expect(api.readNotification).not.toHaveBeenCalled()
    expect(api.readAllNotifications).not.toHaveBeenCalled()
    expect(screen.getByRole('button', {
      name: 'Notificaciones, 1 sin leer',
    })).toBeVisible()

    await user.click(within(dialog).getByRole('button', {
      name: 'Marcar Nueva reserva como leída',
    }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Notificaciones' })).toBeVisible()
    })
  })

  it('searches only the appointments present in the live dashboard payload', async () => {
    const user = userEvent.setup()
    renderDashboard()

    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    await user.type(screen.getByRole('searchbox', { name: 'Buscar en el panel' }), 'moka')

    const results = screen.getByRole('region', { name: 'Resultados de búsqueda' })
    expect(within(results).getByRole('button', { name: /moka.*maría gonzález/i })).toBeVisible()
    expect(within(results).queryByText(/mensaje/i)).not.toBeInTheDocument()
  })

  it('opens lifecycle controls without any destructive reservation action', async () => {
    const user = userEvent.setup()
    renderDashboard()

    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    await user.click(screen.getByRole('button', { name: 'Opciones de Moka' }))

    expect(screen.getByRole('button', { name: 'Ver detalle' })).toBeVisible()
    expect(screen.queryByRole('button', { name: /eliminar reserva/i })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Ver detalle' }))
    const dialog = screen.getByRole('dialog', { name: 'Detalle de la reserva' })
    expect(within(dialog).getByText(/Moka.*Consulta general/i)).toBeVisible()
    expect(within(dialog).getByRole('button', { name: 'Cancelar' })).toBeVisible()
    expect(within(dialog).getByRole('button', { name: 'Reprogramar' })).toBeVisible()
    expect(within(dialog).queryByRole('button', { name: /eliminar reserva/i })).not.toBeInTheDocument()
  })

  it('calls real authentication logout and returns to login', async () => {
    const user = userEvent.setup()
    const logout = vi.fn().mockResolvedValue(true)
    const { router } = renderDashboard({ logout })

    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    await user.click(screen.getByRole('button', { name: 'Abrir menú de usuario' }))
    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/login')
    })
    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('does not redirect after logout completes on an unmounted admin page', async () => {
    const user = userEvent.setup()
    const completion = deferred()
    const logout = vi.fn().mockReturnValue(completion.promise)
    const { router } = renderDashboard({ logout })

    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    await user.click(screen.getByRole('button', { name: 'Abrir menú de usuario' }))
    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
    await act(async () => {
      await router.navigate('/siguiente')
    })
    await screen.findByRole('heading', { name: 'Página siguiente' })

    await act(async () => {
      completion.resolve(true)
      await completion.promise
    })

    expect(router.state.location.pathname).toBe('/siguiente')
    expect(screen.getByRole('heading', { name: 'Página siguiente' })).toBeVisible()
  })

  it('keeps the current logout error when an older attempt settles later', async () => {
    const first = deferred()
    const second = deferred()
    const logout = vi.fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const { router } = renderDashboard({ logout })

    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    await userEvent.click(screen.getByRole('button', { name: 'Abrir menú de usuario' }))
    const logoutButton = screen.getByRole('button', { name: 'Cerrar sesión' })

    await act(async () => {
      logoutButton.click()
      logoutButton.click()
    })
    expect(logout).toHaveBeenCalledTimes(2)

    await act(async () => {
      second.reject(new ApiError('No se pudo cerrar la sesión.', {
        code: 'LOGOUT_FAILED',
        status: 503,
      }))
      try {
        await second.promise
      } catch {
        // The page renders the current external-auth failure.
      }
    })
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cerrar la sesión.')

    await act(async () => {
      first.resolve(true)
      await first.promise
    })

    expect(router.state.location.pathname).toBe('/admin')
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cerrar la sesión.')
  })

  it('shows a retryable error state when the dashboard cannot load', async () => {
    const api = {
      dashboard: vi
        .fn()
        .mockRejectedValueOnce(new Error('Sin conexión'))
        .mockResolvedValueOnce(dashboard),
    }
    const user = userEvent.setup()
    renderDashboard({ api })

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el panel')
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })).toBeVisible()
    expect(api.dashboard).toHaveBeenCalledTimes(2)
  })

  it.each([
    ['sidebar calendar', 'Calendario'],
    ['today agenda card', 'Ver calendario'],
  ])('normalizes the %s path to the Santiago clinic day', async (_, buttonName) => {
    class CrossDayHostDate extends Date {
      getFullYear() {
        return 2026
      }

      getMonth() {
        return 6
      }

      getDate() {
        return 29
      }

      toLocaleDateString() {
        return 'miércoles, 29 de julio'
      }
    }

    const user = userEvent.setup()
    const crossDayNow = new CrossDayHostDate('2026-07-29T02:00:00Z')
    renderDashboard({ currentNow: crossDayNow })

    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    await user.click(screen.getByRole('button', { name: buttonName }))

    const dialog = screen.getByRole('dialog', { name: 'Calendario y agenda' })
    expect(within(dialog).getByText('martes, 28 de julio')).toBeVisible()
    expect(within(dialog).getByText('1 atenciones programadas')).toBeVisible()
  })

  it('opens a read-only detail for an agenda reservation outside the appointment summary', async () => {
    const user = userEvent.setup()
    const scheduleOnlyDashboard = {
      ...dashboard,
      appointments: [],
      schedule: [{
        ...dashboard.schedule[0],
        reservationId: 99,
      }],
    }
    renderDashboard({ currentDashboard: scheduleOnlyDashboard })

    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    await user.click(screen.getByRole('button', { name: /consulta general.*moka/i }))

    const dialog = screen.getByRole('dialog', { name: 'Detalle de la reserva' })
    expect(within(dialog).getByText(/Moka.*Consulta general/i)).toBeVisible()
    expect(within(dialog).getByText('Confirmada')).toBeVisible()
    expect(within(dialog).getByText('Hoy, 15:30')).toBeVisible()
  })

  it('moves focus into the mobile drawer, makes the main inert, and restores the trigger', async () => {
    const originalWidth = window.innerWidth
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      value: 500,
    })

    try {
      const user = userEvent.setup()
      renderDashboard()
      await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })

      const menuTrigger = screen.getByRole('button', { name: 'Abrir menú' })
      const main = document.querySelector('.admin-main')
      await user.click(menuTrigger)

      const sidebar = screen.getByLabelText('Navegación principal')
      const closeButton = within(sidebar).getByRole('button', { name: 'Cerrar menú' })
      expect(closeButton).toHaveFocus()
      expect(main).toHaveAttribute('inert')

      await user.click(closeButton)

      expect(main).not.toHaveAttribute('inert')
      expect(menuTrigger).toHaveFocus()
    } finally {
      Object.defineProperty(window, 'innerWidth', {
        configurable: true,
        value: originalWidth,
      })
    }
  })

  it('focuses replaced modal content and restores the original dashboard trigger once', async () => {
    const user = userEvent.setup()
    renderDashboard()

    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    const trigger = screen.getByRole('button', { name: 'Ver reservas' })
    await user.click(trigger)

    const listDialog = screen.getByRole('dialog', { name: 'Reservas' })
    const restoreSpy = vi.spyOn(trigger, 'focus')
    await user.click(within(listDialog).getByRole('button', { name: /Moka/i }))
    expect(restoreSpy).not.toHaveBeenCalled()

    const detailDialog = screen.getByRole('dialog', { name: 'Detalle de la reserva' })
    expect(detailDialog).toContainElement(document.activeElement)
    expect(detailDialog.querySelector('header button')).toHaveFocus()
    await user.click(detailDialog.querySelector('header button'))

    expect(trigger).toHaveFocus()
    expect(restoreSpy).toHaveBeenCalledTimes(1)
  })

  it('queries and renders a populated selected clinic date with the exact 100-row bound', async () => {
    const future = {
      ...dashboard.appointments[0],
      id: 77,
      startsAt: '2026-07-29T10:00:00-04:00',
      endsAt: '2026-07-29T10:30:00-04:00',
      items: [{
        ...dashboard.appointments[0].items[0],
        petId: 17,
        petName: 'Luna',
      }],
    }
    const api = fakeAdminApi({ dashboard })
    api.reservations.mockResolvedValueOnce({
      content: [future],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    })
    api.reservation.mockResolvedValueOnce({
      ...future,
      cancelledAt: null,
      cancelledBy: null,
      cancellationReason: null,
      events: [],
    })
    const user = userEvent.setup()
    renderDashboard({ api })
    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })

    await user.click(screen.getByRole('button', { name: '29 de Julio' }))
    await user.click(screen.getByRole('button', { name: /Ver agenda del 29 de julio/i }))

    expect(api.reservations).toHaveBeenCalledWith(
      '?from=2026-07-29&to=2026-07-29&page=0&size=100',
    )
    const dialog = await screen.findByRole('dialog', { name: 'Calendario y agenda' })
    await user.click(within(dialog).getByRole('button', { name: /Luna/i }))
    expect(await screen.findByRole('dialog', { name: 'Detalle de la reserva' })).toBeVisible()
    expect(api.reservation).toHaveBeenCalledWith(77)
  })

  it('distinguishes a true empty selected agenda from loading', async () => {
    const pending = deferred()
    const api = fakeAdminApi({ dashboard })
    api.reservations.mockReturnValueOnce(pending.promise)
    const user = userEvent.setup()
    renderDashboard({ api })
    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    await user.click(screen.getByRole('button', { name: '29 de Julio' }))
    await user.click(screen.getByRole('button', { name: /Ver agenda del 29 de julio/i }))

    expect(screen.getByText(/Cargando agenda/)).toBeVisible()
    expect(screen.queryByText(/No hay atenciones programadas/i)).not.toBeInTheDocument()
    await act(async () => {
      pending.resolve({
        content: [],
        page: 0,
        size: 100,
        totalElements: 0,
        totalPages: 0,
      })
      await pending.promise
    })
    expect(await screen.findByText(/No hay atenciones programadas/i)).toBeVisible()
  })

  it('keeps a selected agenda server failure retryable', async () => {
    const api = fakeAdminApi({ dashboard })
    api.reservations
      .mockRejectedValueOnce(new ApiError('No pudimos cargar la agenda.', {
        code: 'AGENDA_UNAVAILABLE',
        status: 503,
      }))
      .mockResolvedValueOnce({
        content: [],
        page: 0,
        size: 100,
        totalElements: 0,
        totalPages: 0,
      })
    const user = userEvent.setup()
    renderDashboard({ api })
    await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })
    await user.click(screen.getByRole('button', { name: '29 de Julio' }))
    await user.click(screen.getByRole('button', { name: /Ver agenda del 29 de julio/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos cargar la agenda.',
    )
    await user.click(screen.getByRole('button', { name: 'Reintentar agenda' }))
    expect(await screen.findByText(/No hay atenciones programadas/i)).toBeVisible()
    expect(api.reservations).toHaveBeenCalledTimes(2)
  })
})

describe('imageKeyForSpecies', () => {
  it.each([
    ['Gato doméstico', 'cat'],
    ['gatO', 'cat'],
    ['Perro', 'dog'],
    ['', 'dog'],
  ])('maps %j to the supported pet image key %j', (species, expected) => {
    expect(imageKeyForSpecies(species)).toBe(expected)
  })
})

import { screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './auth/AuthProvider'
import App from './App'
import { fakeAuth, jsonResponse } from './test/fakes'
import { renderApp } from './test/renderApp'

function authWrapper(api) {
  return function TestAuthWrapper({ children }) {
    return <AuthProvider api={api}>{children}</AuthProvider>
  }
}

function renderWholeApp(route, account = null) {
  return renderApp(<App />, {
    route,
    Wrapper: authWrapper(fakeAuth({ me: account })),
  })
}

const clientAccount = {
  userId: 7,
  clientId: 12,
  email: 'ana@example.cl',
  name: 'Ana Pérez',
  accountType: 'CLIENT',
}

const adminAccount = {
  userId: 1,
  clientId: null,
  email: 'nataly@amidog.cl',
  name: 'Nataly Apablaza',
  accountType: 'ADMIN',
}

const emptyDashboard = {
  profile: {
    name: 'Nataly Apablaza',
    email: 'nataly@amidog.cl',
    role: 'ADMIN',
  },
  stats: { todayAppointments: 0, clients: 0, pets: 0 },
  appointments: [],
  services: [],
  chart: {
    currentWeek: { label: 'Esta semana', labels: [], values: [] },
    previousWeek: { label: 'Semana pasada', labels: [], values: [] },
    currentMonth: { label: 'Este mes', labels: [], values: [] },
  },
  schedule: [],
}

function stubApplicationReads() {
  const responses = {
    '/api/v1/admin/dashboard': emptyDashboard,
    '/api/v1/me/notifications': [],
    '/api/v1/me/pets': [],
    '/api/v1/me/profile': {
      id: 12,
      name: 'Ana Pérez',
      phone: '+56912345678',
      email: 'ana@example.cl',
    },
    '/api/v1/me/reservations': [],
    '/api/v1/services': [],
  }

  vi.spyOn(globalThis, 'fetch').mockImplementation(async (path) => {
    if (!(path in responses)) {
      throw new Error(`Unexpected application read: ${path}`)
    }
    return jsonResponse(responses[path])
  })
}

beforeEach(() => {
  window.scrollTo = vi.fn()
})

describe('application account and protected routes', () => {
  it.each([
    ['/', /cuidamos a tu amidog/i],
    ['/nosotros', /más que una veterinaria/i],
    ['/contacto', /contáctanos/i],
    ['/login', /iniciar sesión/i],
    ['/crear-cuenta', /crear cuenta/i],
    ['/verificar-correo', /verificar correo/i],
    ['/olvide-contrasena', /recuperar contraseña/i],
    ['/restablecer-contrasena', /restablecer contraseña/i],
  ])('renders the public or account route %s through the whole application', async (route, heading) => {
    renderWholeApp(route)
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible()
  })

  it.each([
    ['/panel'],
    ['/reservar'],
    ['/calendario'],
    ['/confirmacion'],
  ])('protects the client route %s with a return path', async (route) => {
    const { router } = renderWholeApp(route)
    expect(await screen.findByRole('heading', { name: /iniciar sesión/i })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe(`?next=${encodeURIComponent(route)}`)
  })

  it.each([
    ['/admin'],
    ['/admin/reservas'],
  ])('protects the administrator route %s with a return path', async (route) => {
    const { router } = renderWholeApp(route)
    expect(await screen.findByRole('heading', { name: /iniciar sesión/i })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe(`?next=${encodeURIComponent(route)}`)
  })

  it('renders a client page through the authenticated client boundary', async () => {
    stubApplicationReads()
    const { router } = renderWholeApp('/reservar', clientAccount)

    expect(await screen.findByRole('heading', {
      name: /elige tus mascotas y servicios/i,
    })).toBeVisible()
    expect(router.state.location.pathname).toBe('/reservar')
  })

  it('renders the live administrator dashboard inside the protected admin route', async () => {
    stubApplicationReads()
    const { router } = renderWholeApp('/admin', adminAccount)

    expect(await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/admin')
  })

  it('keeps a client account out of the administrator route', async () => {
    stubApplicationReads()
    const { router } = renderWholeApp('/admin', clientAccount)

    expect(await screen.findByRole('heading', { name: 'Tus horas' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/panel')
  })

  it('keeps an administrator account out of client routes', async () => {
    stubApplicationReads()
    const { router } = renderWholeApp('/panel', adminAccount)

    expect(await screen.findByRole('heading', { name: 'Bienvenida, Nataly' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/admin')
  })

  it.each([
    ['/pages/nosotros.html', '/nosotros'],
    ['/pages/contacto.html', '/contacto'],
    ['/pages/login.html', '/login'],
    ['/pages/panel.html', '/login'],
    ['/pages/reservation.html', '/login'],
    ['/pages/calendario.html', '/login'],
    ['/pages/confirmacion.html', '/login'],
  ])('continues to redirect legacy route %s', async (legacy, expected) => {
    const { router } = renderWholeApp(legacy)
    if (expected === '/login') {
      await screen.findByRole('heading', { name: /iniciar sesión/i })
    } else {
      await waitFor(() => {
        expect(router.state.location.pathname).toBe(expected)
      })
    }
    expect(router.state.location.pathname).toBe(expected)
  })
})

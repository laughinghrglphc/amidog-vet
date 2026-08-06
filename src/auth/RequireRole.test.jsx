import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from './AuthProvider'
import { safeNextPath } from './authNavigation'
import { RequireRole } from './RequireRole'
import { fakeAuth } from '../test/fakes'
import { renderApp } from '../test/renderApp'

const client = {
  userId: 41,
  clientId: 12,
  email: 'ana@example.cl',
  name: 'Ana Pérez',
  accountType: 'CLIENT',
}

const admin = {
  userId: 1,
  clientId: null,
  email: 'admin@amidog.cl',
  name: 'Administradora AmiDog',
  accountType: 'ADMIN',
}

function authWrapper(api) {
  return function TestAuthWrapper({ children }) {
    return <AuthProvider api={api}>{children}</AuthProvider>
  }
}

function renderGuard({ account = null, route = '/panel' } = {}) {
  const api = fakeAuth({ me: account })
  return renderApp(null, {
    route,
    routes: [
      {
        path: '/panel',
        element: <RequireRole role="CLIENT"><h1>Panel privado</h1></RequireRole>,
      },
      {
        path: '/admin/*',
        element: <RequireRole role="ADMIN"><h1>Administración</h1></RequireRole>,
      },
      { path: '/login', element: <h1>Iniciar sesión</h1> },
    ],
    Wrapper: authWrapper(api),
  })
}

describe('RequireRole', () => {
  it('does not flash protected content while the session is loading', () => {
    const api = fakeAuth()
    api.me.mockReturnValueOnce(new Promise(() => {}))

    renderApp(null, {
      route: '/panel',
      routes: [
        {
          path: '/panel',
          element: <RequireRole role="CLIENT"><h1>Panel privado</h1></RequireRole>,
        },
        { path: '/login', element: <h1>Iniciar sesión</h1> },
      ],
      Wrapper: authWrapper(api),
    })

    expect(screen.queryByRole('heading', { name: 'Panel privado' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Iniciar sesión' })).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/verificando sesión/i)
  })

  it('redirects an anonymous client route with a replace return path', async () => {
    const { router } = renderGuard({ route: '/panel?tab=mascotas#nuevas' })

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe(
      '?next=%2Fpanel%3Ftab%3Dmascotas%23nuevas',
    )
    expect(router.state.historyAction).toBe('REPLACE')
  })

  it.each([
    ['CLIENT', client, '/admin', '/panel'],
    ['ADMIN', admin, '/panel', '/admin'],
  ])('redirects a wrong %s role to its own home without a loop', async (
    role,
    account,
    route,
    destination,
  ) => {
    const { router } = renderGuard({ account, route })

    await screen.findByRole('heading', {
      name: destination === '/admin' ? 'Administración' : 'Panel privado',
    })
    expect(router.state.location.pathname).toBe(destination)
  })

  it('renders protected content only for the requested role', async () => {
    renderGuard({ account: client })
    expect(await screen.findByRole('heading', { name: 'Panel privado' })).toBeVisible()
  })
})

describe('safeNextPath', () => {
  it.each([
    ['/panel', '/panel'],
    ['/reservar?pet=1#servicio', '/reservar?pet=1#servicio'],
    ['/calendario', '/calendario'],
    ['/confirmacion', '/confirmacion'],
  ])('accepts the client application target %s', (candidate, expected) => {
    expect(safeNextPath(candidate, 'CLIENT')).toBe(expected)
  })

  it.each([
    ['https://evil.example/steal'],
    ['//evil.example/steal'],
    ['%2F%2Fevil.example/steal'],
    ['%252F%252Fevil.example/steal'],
    ['/\\evil.example/steal'],
    ['/panel\u0000https://evil.example'],
    ['/admin'],
    ['/contacto'],
    ['panel'],
  ])('rejects unsafe or role-incompatible client target %s', (candidate) => {
    expect(safeNextPath(candidate, 'CLIENT')).toBe('/panel')
  })

  it('accepts only the admin route for administrators', () => {
    expect(safeNextPath('/admin/reservas?q=Milo', 'ADMIN')).toBe(
      '/admin/reservas?q=Milo',
    )
    expect(safeNextPath('/panel', 'ADMIN')).toBe('/admin')
  })
})

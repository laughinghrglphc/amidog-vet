import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { AuthProvider } from '../auth/AuthProvider'
import { RequireRole } from '../auth/RequireRole'
import { fakeAuth } from '../test/fakes'
import { renderApp } from '../test/renderApp'
import Login from './Login'

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

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function authWrapper(api) {
  return function TestAuthWrapper({ children }) {
    return <AuthProvider api={api}>{children}</AuthProvider>
  }
}

function renderLogin({
  api = fakeAuth({ loginResult: client }),
  route = '/login',
} = {}) {
  const view = renderApp(null, {
    route,
    routes: [
      { path: '/login', element: <Login /> },
      { path: '/panel', element: <h1>Panel cliente</h1> },
      { path: '/reservar', element: <h1>Reservar hora</h1> },
      { path: '/calendario', element: <h1>Calendario</h1> },
      { path: '/confirmacion', element: <h1>Confirmación</h1> },
      { path: '/admin/*', element: <h1>Panel administrador</h1> },
    ],
    Wrapper: authWrapper(api),
  })
  return { ...view, api }
}

async function submitCredentials({
  email = 'ana@example.cl',
  password = 'una-clave-segura-2026',
} = {}) {
  await userEvent.type(screen.getByLabelText(/correo electrónico/i), email)
  await userEvent.type(screen.getByLabelText(/^contraseña$/i), password)
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}

describe('Login', () => {
  it('submits credentials and returns a client to an allowed next route', async () => {
    const { api, router } = renderLogin({ route: '/login?next=/calendario' })

    await submitCredentials()

    expect(api.login).toHaveBeenCalledWith(
      'ana@example.cl',
      'una-clave-segura-2026',
    )
    expect(await screen.findByRole('heading', { name: 'Calendario' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/calendario')
  })

  it('always sends an administrator to the administrator home', async () => {
    const api = fakeAuth({ loginResult: admin })
    const { router } = renderLogin({ api, route: '/login?next=/panel' })

    await submitCredentials({
      email: 'admin@amidog.cl',
      password: 'Secret-Password-9!',
    })

    expect(await screen.findByRole('heading', { name: 'Panel administrador' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/admin')
  })

  it('returns an administrator to the protected nested path that initiated login', async () => {
    const api = fakeAuth({ loginResult: admin })
    const { router } = renderApp(null, {
      route: '/admin/reservas?status=PENDING#reserva-301',
      routes: [
        { path: '/login', element: <Login /> },
        {
          path: '/admin/*',
          element: (
            <RequireRole role="ADMIN">
              <h1>Panel administrador</h1>
            </RequireRole>
          ),
        },
      ],
      Wrapper: authWrapper(api),
    })

    expect(await screen.findByRole('heading', { name: /Bienvenido/i })).toBeVisible()
    await submitCredentials({
      email: 'admin@amidog.cl',
      password: 'Secret-Password-9!',
    })

    expect(await screen.findByRole('heading', { name: 'Panel administrador' })).toBeVisible()
    expect(router.state.location).toMatchObject({
      pathname: '/admin/reservas',
      search: '?status=PENDING',
      hash: '#reserva-301',
    })
  })

  it.each([
    ['//evil.example/steal'],
    ['https://evil.example/steal'],
    ['%2F%2Fevil.example/steal'],
    ['/admin'],
    ['/contacto'],
  ])('falls back to the client home for unsafe next value %s', async (next) => {
    const { router } = renderLogin({
      route: `/login?next=${encodeURIComponent(next)}`,
    })

    await submitCredentials()

    expect(await screen.findByRole('heading', { name: 'Panel cliente' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/panel')
  })

  it('prevents a duplicate login submission while the first is pending', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.login.mockReturnValueOnce(pending.promise)
    renderLogin({ api })

    await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'ana@example.cl')
    await userEvent.type(screen.getByLabelText(/^contraseña$/i), 'una-clave-segura-2026')
    const button = screen.getByRole('button', { name: 'Ingresar' })
    await userEvent.dblClick(button)

    expect(api.login).toHaveBeenCalledTimes(1)
    expect(button).toBeDisabled()
    pending.resolve(client)
    expect(await screen.findByRole('heading', { name: 'Panel cliente' })).toBeVisible()
  })

  it.each([
    ['INVALID_CREDENTIALS', 401, 'Correo o contraseña incorrectos, o cuenta aún no verificada.'],
    ['EMAIL_NOT_VERIFIED', 401, 'Debes verificar tu correo antes de ingresar.'],
    ['RATE_LIMITED', 429, 'Demasiados intentos. Intenta nuevamente más tarde.'],
    ['NETWORK_ERROR', 0, 'No pudimos comunicarnos con el servidor.'],
  ])('shows the closed %s error accessibly', async (code, status, message) => {
    const api = fakeAuth()
    api.login.mockRejectedValueOnce(new ApiError(message, { code, status }))
    renderLogin({ api })

    await submitCredentials()

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeEnabled()
  })

  it('does not expose raw errors outside the reviewed API envelope', async () => {
    const api = fakeAuth()
    api.login.mockRejectedValueOnce(new Error('fetch failed with secret reset-token'))
    renderLogin({ api })

    await submitCredentials()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos iniciar sesión. Intenta nuevamente.',
    )
    expect(screen.getByRole('alert')).not.toHaveTextContent('reset-token')
  })

  it('uses server-session controls without remember-me or storage writes', async () => {
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    renderLogin()

    expect(screen.queryByRole('checkbox', { name: /recordarme/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /olvidaste tu contraseña/i })).toHaveAttribute(
      'href',
      '/olvide-contrasena',
    )
    expect(screen.getByRole('link', { name: /crear cuenta/i })).toHaveAttribute(
      'href',
      '/crear-cuenta',
    )
    await submitCredentials()
    expect(storageWrite).not.toHaveBeenCalled()
  })
})

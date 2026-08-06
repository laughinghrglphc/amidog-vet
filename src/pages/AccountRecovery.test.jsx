import { StrictMode } from 'react'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { fakeAuth } from '../test/fakes'
import { renderApp } from '../test/renderApp'
import ForgotPassword from './ForgotPassword'
import ResetPassword from './ResetPassword'
import VerifyEmail from './VerifyEmail'

function strictWrapper({ children }) {
  return <StrictMode>{children}</StrictMode>
}

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('VerifyEmail', () => {
  it('consumes the URL token once under StrictMode and removes it with replace', async () => {
    const api = fakeAuth()
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    const { router } = renderApp(null, {
      route: '/verificar-correo?token=verification-token',
      routes: [
        {
          path: '/verificar-correo',
          element: <VerifyEmail api={api} />,
        },
        { path: '/login', element: <h1>Iniciar sesión</h1> },
      ],
      Wrapper: strictWrapper,
    })

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
    expect(api.verifyEmail).toHaveBeenCalledTimes(1)
    expect(api.verifyEmail).toHaveBeenCalledWith('verification-token')
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('?verified=1')
    expect(router.state.historyAction).toBe('REPLACE')
    expect(router.state.location.search).not.toContain('verification-token')
    expect(storageWrite).not.toHaveBeenCalled()
  })

  it('does not call verification without a token and offers a resend form', async () => {
    const api = fakeAuth()
    renderApp(<VerifyEmail api={api} />, {
      route: '/verificar-correo',
    })

    expect(screen.getByRole('alert')).toHaveTextContent(
      /enlace de verificación no es válido/i,
    )
    expect(api.verifyEmail).not.toHaveBeenCalled()
    expect(screen.getByLabelText(/correo electrónico/i)).toBeVisible()
  })

  it('shows an expired-token envelope and allows enumeration-safe resend', async () => {
    const api = fakeAuth()
    api.verifyEmail.mockRejectedValueOnce(new ApiError(
      'El enlace de verificación no es válido o ya expiró.',
      { code: 'INVALID_VERIFICATION_TOKEN', status: 400 },
    ))
    renderApp(<VerifyEmail api={api} />, {
      route: '/verificar-correo?token=expired-token',
    })

    expect(await screen.findByRole('alert')).toHaveTextContent(/expiró/i)
    await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'ana@example.cl')
    await userEvent.click(screen.getByRole('button', { name: /reenviar verificación/i }))
    expect(api.resendVerification).toHaveBeenCalledWith('ana@example.cl')
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Revisa tu correo para verificar tu cuenta.',
    )
  })

  it('prevents duplicate verification resend requests', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.verifyEmail.mockRejectedValueOnce(new ApiError(
      'El enlace no es válido.',
      { code: 'INVALID_VERIFICATION_TOKEN', status: 400 },
    ))
    api.resendVerification.mockReturnValueOnce(pending.promise)
    renderApp(<VerifyEmail api={api} />, {
      route: '/verificar-correo?token=expired-token',
    })

    await screen.findByRole('alert')
    await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'ana@example.cl')
    const button = screen.getByRole('button', { name: /reenviar verificación/i })
    await userEvent.dblClick(button)
    expect(api.resendVerification).toHaveBeenCalledTimes(1)
    expect(button).toBeDisabled()
  })
})

describe('ForgotPassword', () => {
  it('shows the backend non-enumerating success message', async () => {
    const api = fakeAuth()
    renderApp(<ForgotPassword api={api} />)

    await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'ana@example.cl')
    await userEvent.click(screen.getByRole('button', { name: /enviar instrucciones/i }))

    expect(api.forgotPassword).toHaveBeenCalledWith('ana@example.cl')
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Si la cuenta existe, enviaremos instrucciones al correo.',
    )
  })

  it('shows rate-limit and network envelopes safely and re-enables submission', async () => {
    const api = fakeAuth()
    api.forgotPassword.mockRejectedValueOnce(new ApiError(
      'Demasiados intentos. Intenta nuevamente más tarde.',
      { code: 'RATE_LIMITED', status: 429 },
    ))
    renderApp(<ForgotPassword api={api} />)

    await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'ana@example.cl')
    await userEvent.click(screen.getByRole('button', { name: /enviar instrucciones/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/demasiados intentos/i)
    expect(screen.getByRole('button', { name: /enviar instrucciones/i })).toBeEnabled()
  })

  it('prevents duplicate recovery requests while the first is pending', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.forgotPassword.mockReturnValueOnce(pending.promise)
    renderApp(<ForgotPassword api={api} />)

    await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'ana@example.cl')
    const button = screen.getByRole('button', { name: /enviar instrucciones/i })
    await userEvent.dblClick(button)

    expect(api.forgotPassword).toHaveBeenCalledTimes(1)
    expect(button).toBeDisabled()
    pending.resolve({
      message: 'Si la cuenta existe, enviaremos instrucciones al correo.',
    })
    expect(await screen.findByRole('status')).toBeVisible()
  })
})

describe('ResetPassword', () => {
  it('does not submit when the token is missing', async () => {
    const api = fakeAuth()
    renderApp(<ResetPassword api={api} />, {
      route: '/restablecer-contrasena',
    })

    expect(screen.getByRole('alert')).toHaveTextContent(
      /enlace para restablecer.*no es válido/i,
    )
    expect(api.resetPassword).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: /guardar nueva contraseña/i }))
      .not.toBeInTheDocument()
  })

  it.each([
    ['corta', 'corta', /entre 12 y 128/i],
    ['otra-clave-segura-2026', 'no-coincide-2026', /no coinciden/i],
  ])('validates password pair without consuming the token', async (
    password,
    confirmation,
    expected,
  ) => {
    const api = fakeAuth()
    renderApp(<ResetPassword api={api} />, {
      route: '/restablecer-contrasena?token=reset-token',
    })

    await userEvent.type(screen.getByLabelText(/^nueva contraseña$/i), password)
    await userEvent.type(screen.getByLabelText(/confirmar nueva contraseña/i), confirmation)
    await userEvent.click(screen.getByRole('button', { name: /guardar nueva contraseña/i }))

    expect(screen.getByRole('alert')).toHaveTextContent(expected)
    expect(api.resetPassword).not.toHaveBeenCalled()
  })

  it('passes the URL token directly once and removes it after success', async () => {
    const api = fakeAuth()
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    const { router } = renderApp(null, {
      route: '/restablecer-contrasena?token=reset-token',
      routes: [
        {
          path: '/restablecer-contrasena',
          element: <ResetPassword api={api} />,
        },
        { path: '/login', element: <h1>Iniciar sesión</h1> },
      ],
      Wrapper: strictWrapper,
    })

    await userEvent.type(screen.getByLabelText(/^nueva contraseña$/i), 'otra-clave-segura-2026')
    await userEvent.type(screen.getByLabelText(/confirmar nueva contraseña/i), 'otra-clave-segura-2026')
    await userEvent.dblClick(
      screen.getByRole('button', { name: /guardar nueva contraseña/i }),
    )

    expect(api.resetPassword).toHaveBeenCalledTimes(1)
    expect(api.resetPassword).toHaveBeenCalledWith(
      'reset-token',
      'otra-clave-segura-2026',
    )
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
    expect(router.state.location.search).toBe('?reset=1')
    expect(router.state.historyAction).toBe('REPLACE')
    expect(router.state.location.search).not.toContain('reset-token')
    expect(storageWrite).not.toHaveBeenCalled()
  })

  it('keeps an expired token only in the current URL and shows the closed error', async () => {
    const api = fakeAuth()
    api.resetPassword.mockRejectedValueOnce(new ApiError(
      'El enlace para restablecer la contraseña no es válido o ya expiró.',
      { code: 'INVALID_PASSWORD_RESET_TOKEN', status: 400 },
    ))
    renderApp(<ResetPassword api={api} />, {
      route: '/restablecer-contrasena?token=expired-token',
    })

    await userEvent.type(screen.getByLabelText(/^nueva contraseña$/i), 'otra-clave-segura-2026')
    await userEvent.type(screen.getByLabelText(/confirmar nueva contraseña/i), 'otra-clave-segura-2026')
    await userEvent.click(screen.getByRole('button', { name: /guardar nueva contraseña/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/expiró/i)
  })
})

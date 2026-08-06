import { beforeEach, describe, expect, it } from 'vitest'
import { authApi } from './authApi'
import { resetCsrf } from './http'
import { emptyResponse, jsonResponse } from '../test/fakes'

const csrf = {
  headerName: 'X-XSRF-TOKEN',
  parameterName: '_csrf',
  token: 'csrf-auth-123',
}

beforeEach(() => {
  resetCsrf()
})

describe('authApi backend contract', () => {
  it('loads the current server session with credentials', async () => {
    const user = {
      userId: 41,
      clientId: 12,
      email: 'ana@example.cl',
      name: 'Ana Pérez',
      accountType: 'CLIENT',
    }
    fetch.mockResolvedValueOnce(jsonResponse(user))

    await expect(authApi.me()).resolves.toEqual(user)
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/auth/me',
      expect.objectContaining({
        credentials: 'include',
        method: 'GET',
      }),
    )
  })

  it.each([
    [
      'register',
      [{ email: 'ana@example.cl', password: 'password-segura', name: 'Ana Pérez', phone: '+56912345678' }],
      '/api/v1/auth/register',
      { email: 'ana@example.cl', password: 'password-segura', name: 'Ana Pérez', phone: '+56912345678' },
      jsonResponse({ message: 'Revisa tu correo para verificar tu cuenta.' }, 202),
    ],
    [
      'verifyEmail',
      ['verification-token'],
      '/api/v1/auth/verify-email',
      { token: 'verification-token' },
      emptyResponse(),
    ],
    [
      'resendVerification',
      ['ana@example.cl'],
      '/api/v1/auth/resend-verification',
      { email: 'ana@example.cl' },
      jsonResponse({ message: 'Revisa tu correo para verificar tu cuenta.' }, 202),
    ],
    [
      'forgotPassword',
      ['ana@example.cl'],
      '/api/v1/auth/forgot-password',
      { email: 'ana@example.cl' },
      jsonResponse({ message: 'Si la cuenta existe, enviaremos instrucciones al correo.' }, 202),
    ],
    [
      'resetPassword',
      ['reset-token', 'otra-clave-segura'],
      '/api/v1/auth/reset-password',
      { token: 'reset-token', password: 'otra-clave-segura' },
      emptyResponse(),
    ],
  ])('sends the exact %s request body', async (method, args, path, body, response) => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrf))
      .mockResolvedValueOnce(response)

    await authApi[method](...args)

    expect(fetch).toHaveBeenNthCalledWith(
      2,
      path,
      expect.objectContaining({
        body: JSON.stringify(body),
        credentials: 'include',
        method: 'POST',
      }),
    )
  })

  it('uses the form-encoded login boundary', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrf))
      .mockResolvedValueOnce(jsonResponse({
        userId: 1,
        clientId: null,
        email: 'admin@amidog.cl',
        name: 'Administradora AmiDog',
        accountType: 'ADMIN',
      }))

    await authApi.login(' admin@amidog.cl ', 'Secret-Password-9!')

    const [, options] = fetch.mock.calls[1]
    expect(fetch.mock.calls[1][0]).toBe('/api/v1/auth/login')
    expect(options.headers['Content-Type']).toBe('application/x-www-form-urlencoded')
    expect(options.body.toString()).toBe(
      'username=admin%40amidog.cl&password=Secret-Password-9%21',
    )
  })

  it('logs out through the CSRF-protected session endpoint', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrf))
      .mockResolvedValueOnce(emptyResponse())

    await authApi.logout()

    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/v1/auth/logout',
      expect.objectContaining({
        credentials: 'include',
        method: 'POST',
      }),
    )
  })
})

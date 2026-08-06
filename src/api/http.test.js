import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  apiRequest,
  loginFormEncoded,
  resetCsrf,
} from './http'
import {
  emptyResponse,
  jsonResponse,
  textResponse,
} from '../test/fakes'

const csrfPayload = {
  headerName: 'X-XSRF-TOKEN',
  parameterName: '_csrf',
  token: 'csrf-123',
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

describe('apiRequest', () => {
  beforeEach(() => {
    resetCsrf()
  })

  it('uses credentials for safe requests without bootstrapping CSRF', async () => {
    fetch.mockResolvedValueOnce(jsonResponse([{ id: 7 }]))

    await expect(apiRequest('/api/v1/services')).resolves.toEqual([{ id: 7 }])

    expect(fetch).toHaveBeenCalledOnce()
    expect(fetch).toHaveBeenCalledWith('/api/v1/services', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('fetches CSRF once and sends JSON plus the token on sequential mutations', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfPayload))
      .mockResolvedValueOnce(jsonResponse({ id: 7 }, 201))
      .mockResolvedValueOnce(jsonResponse({ id: 8 }, 201))

    await apiRequest('/api/v1/me/pets', {
      method: 'POST',
      body: { name: 'Milo', species: 'Gato' },
    })
    await apiRequest('/api/v1/me/pets', {
      method: 'POST',
      body: { name: 'Luna', species: 'Perro' },
    })

    expect(fetch).toHaveBeenCalledTimes(3)
    expect(fetch).toHaveBeenNthCalledWith(1, '/api/v1/auth/csrf', {
      credentials: 'include',
    })
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/v1/me/pets',
      expect.objectContaining({
        credentials: 'include',
        method: 'POST',
        headers: expect.objectContaining({
          Accept: 'application/json',
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': 'csrf-123',
        }),
        body: '{"name":"Milo","species":"Gato"}',
      }),
    )
    expect(fetch.mock.calls[2][1].headers['X-XSRF-TOKEN']).toBe('csrf-123')
  })

  it.each([200, 201])(
    'rejects fulfilled status %i when the caller requires exact 202',
    async (status) => {
      fetch
        .mockResolvedValueOnce(jsonResponse(csrfPayload))
        .mockResolvedValueOnce(jsonResponse({
          message: 'Tu consulta fue enviada.',
        }, status))

      await expect(apiRequest('/api/v1/contact', {
        body: {
          email: 'ana@example.com',
          message: 'Necesito consultar por una vacuna.',
          name: 'Ana Pérez',
          website: '',
        },
        expectedStatus: 202,
        method: 'POST',
      })).rejects.toMatchObject({
        code: 'UNEXPECTED_RESPONSE_STATUS',
        message: 'El servidor devolvió un estado inesperado.',
        status,
      })
    },
  )

  it('deduplicates concurrent CSRF bootstrap requests', async () => {
    fetch.mockImplementation((path) => {
      if (path === '/api/v1/auth/csrf') {
        return Promise.resolve(jsonResponse(csrfPayload))
      }
      return Promise.resolve(jsonResponse({ ok: true }))
    })

    await Promise.all([
      apiRequest('/api/v1/me/notifications/read-all', { method: 'POST' }),
      apiRequest('/api/v1/me/pets/4', { method: 'DELETE' }),
    ])

    expect(fetch.mock.calls.filter(([path]) => path === '/api/v1/auth/csrf')).toHaveLength(1)
    for (const [path, options] of fetch.mock.calls.filter(([path]) => path !== '/api/v1/auth/csrf')) {
      expect(path).toMatch(/^\/api\/v1\/me\//)
      expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-123')
    }
  })

  it('keeps the post-reset CSRF token when bootstraps complete newest first', async () => {
    const staleCsrf = deferred()
    const currentCsrf = deferred()
    const apiTokens = []
    let csrfCalls = 0
    fetch.mockImplementation((path, options) => {
      if (path === '/api/v1/auth/csrf') {
        csrfCalls += 1
        return csrfCalls === 1 ? staleCsrf.promise : currentCsrf.promise
      }
      apiTokens.push(options.headers['X-XSRF-TOKEN'])
      return Promise.resolve(jsonResponse({ ok: true }))
    })

    const staleRequest = apiRequest('/api/v1/me/pets/1', { method: 'DELETE' })
    resetCsrf()
    const currentRequest = apiRequest('/api/v1/me/pets/2', { method: 'DELETE' })

    currentCsrf.resolve(jsonResponse({ ...csrfPayload, token: 'current-token' }))
    await currentRequest
    staleCsrf.resolve(jsonResponse({ ...csrfPayload, token: 'stale-token' }))
    await staleRequest
    await apiRequest('/api/v1/me/pets/3', { method: 'DELETE' })

    expect(csrfCalls).toBe(2)
    expect(apiTokens).toEqual(['current-token', 'stale-token', 'current-token'])
  })

  it('keeps the newer in-flight CSRF promise when the stale bootstrap finishes first', async () => {
    const staleCsrf = deferred()
    const currentCsrf = deferred()
    const apiTokens = []
    let csrfCalls = 0
    fetch.mockImplementation((path, options) => {
      if (path === '/api/v1/auth/csrf') {
        csrfCalls += 1
        return csrfCalls === 1 ? staleCsrf.promise : currentCsrf.promise
      }
      apiTokens.push(options.headers['X-XSRF-TOKEN'])
      return Promise.resolve(jsonResponse({ ok: true }))
    })

    const staleRequest = apiRequest('/api/v1/me/pets/1', { method: 'DELETE' })
    resetCsrf()
    const currentRequest = apiRequest('/api/v1/me/pets/2', { method: 'DELETE' })

    staleCsrf.resolve(jsonResponse({ ...csrfPayload, token: 'stale-token' }))
    await staleRequest
    const followerRequest = apiRequest('/api/v1/me/pets/3', { method: 'DELETE' })
    expect(csrfCalls).toBe(2)
    currentCsrf.resolve(jsonResponse({ ...csrfPayload, token: 'current-token' }))
    await Promise.all([currentRequest, followerRequest])

    expect(csrfCalls).toBe(2)
    expect(apiTokens).toEqual(['stale-token', 'current-token', 'current-token'])
  })

  it('clears a failed CSRF bootstrap so a later mutation can retry', async () => {
    fetch
      .mockResolvedValueOnce(textResponse('upstream unavailable', 503))
      .mockResolvedValueOnce(jsonResponse(csrfPayload))
      .mockResolvedValueOnce(jsonResponse({ id: 7 }, 201))

    await expect(apiRequest('/api/v1/me/pets', {
      method: 'POST',
      body: { name: 'Milo', species: 'Gato' },
    })).rejects.toMatchObject({
      name: 'ApiError',
      status: 503,
      code: 'HTTP_ERROR',
      message: 'No pudimos iniciar una solicitud segura.',
    })

    await expect(apiRequest('/api/v1/me/pets', {
      method: 'POST',
      body: { name: 'Milo', species: 'Gato' },
    })).resolves.toEqual({ id: 7 })
    expect(fetch.mock.calls.filter(([path]) => path === '/api/v1/auth/csrf')).toHaveLength(2)
  })

  it('returns undefined for 204 responses without parsing a body', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfPayload))
      .mockResolvedValueOnce(emptyResponse())

    await expect(apiRequest('/api/v1/me/pets/7', { method: 'DELETE' }))
      .resolves.toBeUndefined()
  })

  it('returns undefined for a successful HEAD response', async () => {
    fetch.mockResolvedValueOnce(emptyResponse(200))

    await expect(apiRequest('/health', { method: 'HEAD' })).resolves.toBeUndefined()
  })

  it('treats OPTIONS as safe and does not bootstrap CSRF', async () => {
    fetch.mockResolvedValueOnce(jsonResponse({ methods: ['GET', 'POST'] }))

    await expect(apiRequest('/api/v1/services', { method: 'OPTIONS' }))
      .resolves.toEqual({ methods: ['GET', 'POST'] })

    expect(fetch).toHaveBeenCalledOnce()
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/services',
      expect.objectContaining({ method: 'OPTIONS' }),
    )
  })

  it('normalizes structured backend errors and preserves reviewed details', async () => {
    fetch.mockResolvedValueOnce(jsonResponse({
      code: 'BLOCK_OVERLAPS_RESERVATIONS',
      message: 'El bloqueo se superpone con reservas activas.',
      errors: {},
      details: { reservationIds: [301, 302] },
    }, 409))

    await expect(apiRequest('/api/v1/admin/availability/blocks'))
      .rejects.toEqual(expect.objectContaining({
        name: 'ApiError',
        status: 409,
        code: 'BLOCK_OVERLAPS_RESERVATIONS',
        message: 'El bloqueo se superpone con reservas activas.',
        errors: {},
        details: { reservationIds: [301, 302] },
      }))
  })

  it('does not expose a non-JSON error body', async () => {
    fetch.mockResolvedValueOnce(textResponse(
      'password=secret SQL constraint reservation_no_overlap stack trace',
      500,
    ))

    let error
    try {
      await apiRequest('/api/v1/services')
    } catch (caught) {
      error = caught
    }

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 500,
      code: 'HTTP_ERROR',
      errors: {},
      message: 'No se pudo completar la solicitud.',
    })
    expect(JSON.stringify(error)).not.toMatch(/password|constraint|stack trace|secret/i)
  })

  it('normalizes network failures without exposing the transport exception', async () => {
    fetch.mockRejectedValueOnce(new TypeError('fetch failed for https://secret.internal'))

    await expect(apiRequest('/api/v1/services')).rejects.toMatchObject({
      name: 'ApiError',
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'No pudimos comunicarnos con el servidor.',
    })
  })

  it('dispatches the unauthorized event exactly once for a 401 response', async () => {
    let unauthorizedEvents = 0
    const onUnauthorized = () => {
      unauthorizedEvents += 1
    }
    window.addEventListener('amidog:unauthorized', onUnauthorized)
    fetch.mockResolvedValueOnce(jsonResponse({
      code: 'UNAUTHENTICATED',
      message: 'Debes iniciar sesión.',
      errors: {},
    }, 401))

    try {
      await expect(apiRequest('/api/v1/me/profile')).rejects.toMatchObject({
        status: 401,
        code: 'UNAUTHENTICATED',
      })
      expect(unauthorizedEvents).toBe(1)
    } finally {
      window.removeEventListener('amidog:unauthorized', onUnauthorized)
    }
  })

  it('treats method names case-insensitively when deciding whether CSRF is required', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfPayload))
      .mockResolvedValueOnce(jsonResponse({ markedRead: 1 }))

    await apiRequest('/api/v1/me/notifications/read-all', { method: 'post' })

    expect(fetch.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-123' }),
    })
  })

  it('does not write CSRF tokens, request bodies, or responses to browser storage', async () => {
    const localWrite = vi.spyOn(Storage.prototype, 'setItem')
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfPayload))
      .mockResolvedValueOnce(jsonResponse({ id: 7 }, 201))

    await apiRequest('/api/v1/me/pets', {
      method: 'POST',
      body: { name: 'Milo', species: 'Gato' },
    })

    expect(localWrite).not.toHaveBeenCalled()
  })
})

describe('loginFormEncoded', () => {
  beforeEach(() => {
    resetCsrf()
  })

  it('sends trimmed form credentials with CSRF and resets the token after success', async () => {
    const auth = {
      userId: 41,
      clientId: 12,
      email: 'ana@example.cl',
      name: 'Ana Pérez',
      accountType: 'CLIENT',
    }
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfPayload))
      .mockResolvedValueOnce(jsonResponse(auth))
      .mockResolvedValueOnce(jsonResponse({
        ...csrfPayload,
        token: 'csrf-after-login',
      }))
      .mockResolvedValueOnce(jsonResponse({ id: 7 }, 201))

    await expect(loginFormEncoded('  ana@example.cl ', 'una clave & segura'))
      .resolves.toEqual(auth)
    await apiRequest('/api/v1/me/pets', {
      method: 'POST',
      body: { name: 'Milo', species: 'Gato' },
    })

    expect(fetch.mock.calls.filter(([path]) => path === '/api/v1/auth/csrf')).toHaveLength(2)
    const [loginPath, loginOptions] = fetch.mock.calls[1]
    expect(loginPath).toBe('/api/v1/auth/login')
    expect(loginOptions).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-XSRF-TOKEN': 'csrf-123',
      },
    })
    expect(loginOptions.body).toBeInstanceOf(URLSearchParams)
    expect(loginOptions.body.toString())
      .toBe('username=ana%40example.cl&password=una+clave+%26+segura')
    expect(fetch.mock.calls[3][1].headers['X-XSRF-TOKEN']).toBe('csrf-after-login')
  })

  it('normalizes a non-JSON login failure without leaking its response body', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfPayload))
      .mockResolvedValueOnce(textResponse('invalid secret password stack', 401))

    await expect(loginFormEncoded('ana@example.cl', 'wrong-password'))
      .rejects.toMatchObject({
        name: 'ApiError',
        status: 401,
        code: 'HTTP_ERROR',
        message: 'No pudimos iniciar sesión.',
      })
  })

  it('does not persist login credentials or the authentication response', async () => {
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfPayload))
      .mockResolvedValueOnce(jsonResponse({
        userId: 41,
        clientId: null,
        email: 'admin@example.cl',
        name: 'Administradora AmiDog',
        accountType: 'ADMIN',
      }))

    await loginFormEncoded('admin@example.cl', 'do-not-store-this-password')

    expect(storageWrite).not.toHaveBeenCalled()
  })

  it('resets cached CSRF after a successful logout request', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfPayload))
      .mockResolvedValueOnce(emptyResponse())
      .mockResolvedValueOnce(jsonResponse({
        ...csrfPayload,
        token: 'csrf-after-logout',
      }))
      .mockResolvedValueOnce(jsonResponse({ markedRead: 1 }))

    await apiRequest('/api/v1/auth/logout', { method: 'POST' })
    await apiRequest('/api/v1/me/notifications/read-all', { method: 'POST' })

    expect(fetch.mock.calls.filter(([path]) => path === '/api/v1/auth/csrf')).toHaveLength(2)
    expect(fetch.mock.calls[3][1].headers['X-XSRF-TOKEN']).toBe('csrf-after-logout')
  })
})

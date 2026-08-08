const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])
const DEFAULT_ERROR_MESSAGE = 'No se pudo completar la solicitud.'
const NETWORK_ERROR_MESSAGE = 'No pudimos comunicarnos con el servidor.'
const CSRF_ERROR_MESSAGE = 'No pudimos iniciar una solicitud segura.'
const INVALID_RESPONSE_MESSAGE = 'El servidor devolvió una respuesta inválida.'
const UNEXPECTED_RESPONSE_STATUS_MESSAGE = 'El servidor devolvió un estado inesperado.'

export class ApiError extends Error {
  constructor(message, {
    code = 'HTTP_ERROR',
    details,
    errors = {},
    status = 0,
  } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.errors = errors
    this.status = status
    if (details !== undefined) {
      this.details = details
    }
  }
}

let csrf = null
let csrfPromise = null
let csrfGeneration = 0

export function resetCsrf() {
  csrf = null
  csrfPromise = null
  csrfGeneration += 1
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function safeCode(value) {
  return typeof value === 'string' && /^[A-Z][A-Z0-9_]{0,99}$/.test(value)
    ? value
    : 'HTTP_ERROR'
}

async function readJson(response) {
  try {
    return await response.json()
  } catch {
    return null
  }
}

function errorFromPayload(payload, status, fallbackMessage) {
  const isEnvelope = isRecord(payload)
    && typeof payload.message === 'string'
    && safeCode(payload.code) === payload.code
    && isRecord(payload.errors)

  return new ApiError(
    isEnvelope && payload.message.trim()
      ? payload.message
      : fallbackMessage,
    {
      code: isEnvelope ? payload.code : 'HTTP_ERROR',
      details: isEnvelope && isRecord(payload.details) ? payload.details : undefined,
      errors: isEnvelope ? payload.errors : {},
      status,
    },
  )
}

async function requestOrNetworkError(path, options, message = NETWORK_ERROR_MESSAGE) {
  try {
    return await fetch(path, options)
  } catch {
    throw new ApiError(message, { code: 'NETWORK_ERROR' })
  }
}

async function csrfHeader() {
  if (csrf) {
    return csrf
  }
  if (csrfPromise) {
    return csrfPromise
  }

  const generation = csrfGeneration
  const pending = (async () => {
    const response = await requestOrNetworkError(
      '/api/v1/auth/csrf',
      { credentials: 'include' },
      CSRF_ERROR_MESSAGE,
    )
    if (!response.ok) {
      const payload = await readJson(response)
      throw errorFromPayload(payload, response.status, CSRF_ERROR_MESSAGE)
    }

    const payload = await readJson(response)
    if (!isRecord(payload)
      || typeof payload.headerName !== 'string'
      || !/^[A-Za-z0-9-]+$/.test(payload.headerName)
      || typeof payload.token !== 'string'
      || payload.token.length === 0) {
      throw new ApiError(CSRF_ERROR_MESSAGE, {
        code: 'INVALID_CSRF_RESPONSE',
        status: response.status,
      })
    }

    const header = [payload.headerName, payload.token]
    if (generation === csrfGeneration) {
      csrf = header
    }
    return header
  })()

  csrfPromise = pending
  try {
    return await pending
  } finally {
    if (csrfPromise === pending) {
      csrfPromise = null
    }
  }
}

function normalizeHeaders(headers) {
  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries())
  }
  return { ...headers }
}

async function successfulJson(response) {
  const payload = await readJson(response)
  if (payload === null) {
    throw new ApiError(INVALID_RESPONSE_MESSAGE, {
      code: 'INVALID_RESPONSE',
      status: response.status,
    })
  }
  return payload
}

export async function apiRequest(
  path,
  {
    body,
    expectedStatus,
    headers = {},
    method = 'GET',
  } = {},
) {
  const normalizedMethod = String(method).toUpperCase()
  const options = {
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...normalizeHeaders(headers),
    },
    method: normalizedMethod,
  }

  if (!SAFE_METHODS.has(normalizedMethod)) {
    const [name, value] = await csrfHeader()
    options.headers[name] = value
  }

  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }

  const response = await requestOrNetworkError(path, options)
  if (!response.ok) {
    const payload = await readJson(response)
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent('amidog:unauthorized'))
    }
    throw errorFromPayload(payload, response.status, DEFAULT_ERROR_MESSAGE)
  }

  if (expectedStatus !== undefined && response.status !== expectedStatus) {
    throw new ApiError(UNEXPECTED_RESPONSE_STATUS_MESSAGE, {
      code: 'UNEXPECTED_RESPONSE_STATUS',
      status: response.status,
    })
  }

  if (path === '/api/v1/auth/logout' && normalizedMethod === 'POST') {
    resetCsrf()
  }
  if (response.status === 204 || normalizedMethod === 'HEAD') {
    return undefined
  }
  return successfulJson(response)
}

export async function loginFormEncoded(email, password) {
  const [headerName, token] = await csrfHeader()
  const response = await requestOrNetworkError('/api/v1/auth/login', {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/x-www-form-urlencoded',
      [headerName]: token,
    },
    body: new URLSearchParams({
      username: String(email ?? '').trim(),
      password: String(password ?? ''),
    }),
  })
  const payload = await readJson(response)
  if (!response.ok) {
    throw errorFromPayload(payload, response.status, 'No pudimos iniciar sesión.')
  }
  if (payload === null) {
    throw new ApiError(INVALID_RESPONSE_MESSAGE, {
      code: 'INVALID_RESPONSE',
      status: response.status,
    })
  }
  resetCsrf()
  return payload
}

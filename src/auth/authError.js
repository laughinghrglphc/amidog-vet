import { ApiError } from '../api/http'

export function safeAuthError(error, fallback) {
  if (!(error instanceof ApiError) || typeof error.message !== 'string') {
    return { errors: {}, message: fallback }
  }

  const errors = Object.fromEntries(
    Object.entries(error.errors ?? {})
      .filter(([field, message]) => (
        typeof field === 'string'
        && typeof message === 'string'
        && field.length <= 100
        && message.length <= 500
      )),
  )

  return {
    errors,
    message: error.message.trim() || fallback,
  }
}

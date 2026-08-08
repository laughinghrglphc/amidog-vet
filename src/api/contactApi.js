import { ApiError, apiRequest } from './http'

export const contactApi = {
  send: async (body) => {
    const response = await apiRequest('/api/v1/contact', {
      body,
      expectedStatus: 202,
      method: 'POST',
    })
    if (typeof response?.message !== 'string' || !response.message.trim()) {
      throw new ApiError(
        'No pudimos confirmar el envío. Intenta nuevamente.',
        { code: 'INVALID_CONTACT_RESPONSE', status: 202 },
      )
    }
    return {
      message: response.message.trim(),
      status: 202,
    }
  },
}

export function whatsappUrl(number, message) {
  const digits = String(number || '').replace(/\D/g, '')
  if (!digits) return null
  return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`
}

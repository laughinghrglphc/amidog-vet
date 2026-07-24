import {
  apiRequest,
  loginFormEncoded,
} from './http'

export const authApi = {
  me: () => apiRequest('/api/v1/auth/me'),
  register: (body) => apiRequest('/api/v1/auth/register', {
    body,
    method: 'POST',
  }),
  verifyEmail: (token) => apiRequest('/api/v1/auth/verify-email', {
    body: { token },
    method: 'POST',
  }),
  resendVerification: (email) => apiRequest('/api/v1/auth/resend-verification', {
    body: { email },
    method: 'POST',
  }),
  forgotPassword: (email) => apiRequest('/api/v1/auth/forgot-password', {
    body: { email },
    method: 'POST',
  }),
  resetPassword: (token, password) => apiRequest('/api/v1/auth/reset-password', {
    body: { token, password },
    method: 'POST',
  }),
  login: loginFormEncoded,
  logout: () => apiRequest('/api/v1/auth/logout', { method: 'POST' }),
}

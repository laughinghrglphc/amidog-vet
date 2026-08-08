import { apiRequest } from './http'

export const clientApi = {
  profile: () => apiRequest('/api/v1/me/profile'),
  updateProfile: (body) => apiRequest('/api/v1/me/profile', {
    body,
    method: 'PATCH',
  }),
  pets: () => apiRequest('/api/v1/me/pets'),
  createPet: (body) => apiRequest('/api/v1/me/pets', {
    body,
    method: 'POST',
  }),
  updatePet: (id, body) => apiRequest(`/api/v1/me/pets/${id}`, {
    body,
    method: 'PATCH',
  }),
  archivePet: (id) => apiRequest(`/api/v1/me/pets/${id}`, {
    method: 'DELETE',
  }),
  reservations: () => apiRequest('/api/v1/me/reservations'),
  cancelReservation: (id, reason) => apiRequest(
    `/api/v1/me/reservations/${id}/cancel`,
    {
      body: { reason },
      method: 'PATCH',
    },
  ),
  rescheduleReservation: (id, startsAt) => apiRequest(
    `/api/v1/me/reservations/${id}/reschedule`,
    {
      body: { startsAt },
      method: 'PATCH',
    },
  ),
  notifications: () => apiRequest('/api/v1/me/notifications'),
  readNotification: (id) => apiRequest(
    `/api/v1/me/notifications/${id}/read`,
    { method: 'PATCH' },
  ),
  readAllNotifications: () => apiRequest(
    '/api/v1/me/notifications/read-all',
    { method: 'POST' },
  ),
}

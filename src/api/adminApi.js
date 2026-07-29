import { apiRequest } from './http'

export const adminApi = {
  dashboard: () => apiRequest('/api/v1/admin/dashboard'),
  reservations: (query = '') => apiRequest(`/api/v1/admin/reservations${query}`),
  reservation: (id) => apiRequest(`/api/v1/admin/reservations/${id}`),
  changeStatus: (id, body) => apiRequest(`/api/v1/admin/reservations/${id}/status`, {
    body,
    method: 'PATCH',
  }),
  reschedule: (id, startsAt) =>
    apiRequest(`/api/v1/admin/reservations/${id}/reschedule`, {
      body: { startsAt },
      method: 'PATCH',
    }),
  clients: (query = '') => apiRequest(`/api/v1/admin/clients${query}`),
  client: (id) => apiRequest(`/api/v1/admin/clients/${id}`),
  updateClient: (id, body) => apiRequest(`/api/v1/admin/clients/${id}`, {
    body,
    method: 'PATCH',
  }),
  pets: (query = '') => apiRequest(`/api/v1/admin/pets${query}`),
  pet: (id) => apiRequest(`/api/v1/admin/pets/${id}`),
  updatePet: (id, body) => apiRequest(`/api/v1/admin/pets/${id}`, {
    body,
    method: 'PATCH',
  }),
  services: () => apiRequest('/api/v1/admin/services'),
  createService: (body) => apiRequest('/api/v1/admin/services', {
    body,
    method: 'POST',
  }),
  updateService: (id, body) => apiRequest(`/api/v1/admin/services/${id}`, {
    body,
    method: 'PATCH',
  }),
  archiveService: (id) => apiRequest(`/api/v1/admin/services/${id}`, {
    method: 'DELETE',
  }),
  weeklyAvailability: () => apiRequest('/api/v1/admin/availability/weekly'),
  replaceWeeklyAvailability: (body) =>
    apiRequest('/api/v1/admin/availability/weekly', {
      body,
      method: 'PUT',
    }),
  blocks: () => apiRequest('/api/v1/admin/availability/blocks'),
  createBlock: (body) => apiRequest('/api/v1/admin/availability/blocks', {
    body,
    method: 'POST',
  }),
  deleteBlock: (id) => apiRequest(`/api/v1/admin/availability/blocks/${id}`, {
    method: 'DELETE',
  }),
  notifications: () => apiRequest('/api/v1/admin/notifications'),
  readNotification: (id) =>
    apiRequest(`/api/v1/admin/notifications/${id}/read`, {
      method: 'PATCH',
    }),
  readAllNotifications: () =>
    apiRequest('/api/v1/admin/notifications/read-all', {
      method: 'POST',
    }),
}

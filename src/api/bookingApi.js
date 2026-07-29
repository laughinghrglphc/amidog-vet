import { apiRequest } from './http'

export const bookingApi = {
  pets: () => apiRequest('/api/v1/me/pets'),
  services: () => apiRequest('/api/v1/services'),
  availability: (from, to) => apiRequest(
    `/api/v1/availability?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  ),
  createReservation: (draft) => apiRequest('/api/v1/me/reservations', {
    method: 'POST',
    body: {
      startsAt: draft.startsAt,
      items: draft.items.map(({ petId, serviceId }) => ({ petId, serviceId })),
      note: draft.note || null,
    },
  }),
}

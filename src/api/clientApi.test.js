import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './http'
import { clientApi } from './clientApi'

vi.mock('./http', () => ({
  apiRequest: vi.fn(),
}))

describe('clientApi', () => {
  beforeEach(() => {
    apiRequest.mockReset()
    apiRequest.mockResolvedValue({ ok: true })
  })

  it.each([
    ['profile', [], '/api/v1/me/profile', undefined],
    ['updateProfile', [{ name: 'Ana Pérez', phone: '+56912345678' }], '/api/v1/me/profile', {
      body: { name: 'Ana Pérez', phone: '+56912345678' },
      method: 'PATCH',
    }],
    ['pets', [], '/api/v1/me/pets', undefined],
    ['createPet', [{ name: 'Milo', species: 'Gato', breed: null, birthdate: null }], '/api/v1/me/pets', {
      body: { name: 'Milo', species: 'Gato', breed: null, birthdate: null },
      method: 'POST',
    }],
    ['updatePet', [84, { name: 'Milo', species: 'Gato', breed: 'Mestizo', birthdate: '2022-05-10' }], '/api/v1/me/pets/84', {
      body: { name: 'Milo', species: 'Gato', breed: 'Mestizo', birthdate: '2022-05-10' },
      method: 'PATCH',
    }],
    ['archivePet', [84], '/api/v1/me/pets/84', { method: 'DELETE' }],
    ['reservations', [], '/api/v1/me/reservations', undefined],
    ['cancelReservation', [301, null], '/api/v1/me/reservations/301/cancel', {
      body: { reason: null },
      method: 'PATCH',
    }],
    ['rescheduleReservation', [301, '2026-08-11T11:30:00-04:00'], '/api/v1/me/reservations/301/reschedule', {
      body: { startsAt: '2026-08-11T11:30:00-04:00' },
      method: 'PATCH',
    }],
    ['notifications', [], '/api/v1/me/notifications', undefined],
    ['readNotification', [901], '/api/v1/me/notifications/901/read', { method: 'PATCH' }],
    ['readAllNotifications', [], '/api/v1/me/notifications/read-all', { method: 'POST' }],
  ])('maps %s to the exact accepted route and request', async (method, args, path, options) => {
    await clientApi[method](...args)

    if (options === undefined) {
      expect(apiRequest).toHaveBeenCalledWith(path)
    } else {
      expect(apiRequest).toHaveBeenCalledWith(path, options)
    }
  })

  it('preserves the undefined result from a 204 archive response', async () => {
    apiRequest.mockResolvedValueOnce(undefined)

    await expect(clientApi.archivePet(84)).resolves.toBeUndefined()
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))

vi.mock('./http', () => ({ apiRequest }))

import { adminApi } from './adminApi'

describe('adminApi route contract', () => {
  beforeEach(() => {
    apiRequest.mockReset()
  })

  it.each([
    ['dashboard', [], '/api/v1/admin/dashboard', undefined],
    ['reservations', ['?page=1&size=10'], '/api/v1/admin/reservations?page=1&size=10', undefined],
    ['reservation', [301], '/api/v1/admin/reservations/301', undefined],
    ['clients', ['?active=true'], '/api/v1/admin/clients?active=true', undefined],
    ['client', [12], '/api/v1/admin/clients/12', undefined],
    ['pets', ['?clientId=12'], '/api/v1/admin/pets?clientId=12', undefined],
    ['pet', [84], '/api/v1/admin/pets/84', undefined],
    ['services', [], '/api/v1/admin/services', undefined],
    ['weeklyAvailability', [], '/api/v1/admin/availability/weekly', undefined],
    ['blocks', [], '/api/v1/admin/availability/blocks', undefined],
    ['notifications', [], '/api/v1/admin/notifications', undefined],
  ])('%s sends the documented read request', async (method, args, path, options) => {
    await adminApi[method](...args)
    expect(apiRequest).toHaveBeenCalledWith(path, ...(options ? [options] : []))
  })

  it.each([
    ['changeStatus', [301, { status: 'CONFIRMED', reason: '' }], '/api/v1/admin/reservations/301/status', { method: 'PATCH', body: { status: 'CONFIRMED', reason: '' } }],
    ['reschedule', [301, '2026-08-11T11:30:00-04:00'], '/api/v1/admin/reservations/301/reschedule', { method: 'PATCH', body: { startsAt: '2026-08-11T11:30:00-04:00' } }],
    ['updateClient', [12, { name: 'Ana Pérez', phone: '+56912345678', active: true }], '/api/v1/admin/clients/12', { method: 'PATCH', body: { name: 'Ana Pérez', phone: '+56912345678', active: true } }],
    ['updatePet', [84, { name: 'Milo', species: 'Gato', breed: null, birthdate: null }], '/api/v1/admin/pets/84', { method: 'PATCH', body: { name: 'Milo', species: 'Gato', breed: null, birthdate: null } }],
    ['createService', [{ code: 'consulta', name: 'Consulta', description: null, displayOrder: 10 }], '/api/v1/admin/services', { method: 'POST', body: { code: 'consulta', name: 'Consulta', description: null, displayOrder: 10 } }],
    ['updateService', [7, { name: 'Consulta', description: null, displayOrder: 10, active: true }], '/api/v1/admin/services/7', { method: 'PATCH', body: { name: 'Consulta', description: null, displayOrder: 10, active: true } }],
    ['archiveService', [7], '/api/v1/admin/services/7', { method: 'DELETE' }],
    ['replaceWeeklyAvailability', [[{ dayOfWeek: 1, start: '09:00:00', end: '13:00:00', active: true }]], '/api/v1/admin/availability/weekly', { method: 'PUT', body: [{ dayOfWeek: 1, start: '09:00:00', end: '13:00:00', active: true }] }],
    ['createBlock', [{ startsAt: '2026-08-10T10:00:00-04:00', endsAt: '2026-08-10T12:00:00-04:00', reason: null }], '/api/v1/admin/availability/blocks', { method: 'POST', body: { startsAt: '2026-08-10T10:00:00-04:00', endsAt: '2026-08-10T12:00:00-04:00', reason: null } }],
    ['deleteBlock', [9], '/api/v1/admin/availability/blocks/9', { method: 'DELETE' }],
    ['readNotification', [901], '/api/v1/admin/notifications/901/read', { method: 'PATCH' }],
    ['readAllNotifications', [], '/api/v1/admin/notifications/read-all', { method: 'POST' }],
  ])('%s sends the documented mutation', async (method, args, path, options) => {
    await adminApi[method](...args)
    expect(apiRequest).toHaveBeenCalledWith(path, options)
  })
})

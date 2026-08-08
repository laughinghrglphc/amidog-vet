import { describe, expect, it } from 'vitest'
import { fakeAdminApi, fakeClientApi } from './fakes'

describe('contract-faithful API fakes', () => {
  it('returns complete client mutation DTOs by default', async () => {
    const api = fakeClientApi()

    await expect(api.createPet({})).resolves.toEqual(expect.objectContaining({
      id: expect.any(Number),
      active: expect.any(Boolean),
    }))
    await expect(api.cancelReservation(301, {})).resolves.toEqual(
      expect.objectContaining({
        id: expect.any(Number),
        status: 'CANCELLED',
        updatedAt: expect.any(String),
      }),
    )
    await expect(api.rescheduleReservation(301, '2026-08-11T11:30:00-04:00'))
      .resolves.toEqual(expect.objectContaining({
        id: expect.any(Number),
        startsAt: expect.any(String),
        endsAt: expect.any(String),
        status: expect.any(String),
      }))
    await expect(api.readNotification(1)).resolves.toEqual(expect.objectContaining({
      id: expect.any(Number),
      unread: false,
    }))
  })

  it('returns every documented administrator mutation DTO except true 204 operations', async () => {
    const api = fakeAdminApi()
    const body = {
      name: 'Consulta',
      description: null,
      displayOrder: 10,
      active: true,
    }

    await expect(api.changeStatus(301, { status: 'CONFIRMED', reason: null }))
      .resolves.toEqual(expect.objectContaining({
        id: 301,
        previousStatus: expect.any(String),
        status: 'CONFIRMED',
        updatedAt: expect.any(String),
      }))
    await expect(api.reschedule(301, '2026-08-11T11:30:00-04:00'))
      .resolves.toEqual(expect.objectContaining({
        id: 301,
        startsAt: expect.any(String),
        endsAt: expect.any(String),
      }))
    await expect(api.updateClient(12, {
      name: 'Ana',
      phone: '+56912345678',
      active: true,
    })).resolves.toEqual(expect.objectContaining({
      id: 12,
      email: expect.any(String),
      active: true,
    }))
    await expect(api.updatePet(84, {
      name: 'Milo',
      species: 'Gato',
      breed: null,
      birthdate: null,
    })).resolves.toEqual(expect.objectContaining({
      id: 84,
      clientId: expect.any(Number),
      ownerName: expect.any(String),
    }))
    await expect(api.createService({ ...body, code: 'consulta' }))
      .resolves.toEqual(expect.objectContaining({
        id: expect.any(Number),
        code: 'consulta',
        active: true,
      }))
    await expect(api.updateService(7, body)).resolves.toEqual(
      expect.objectContaining({ id: 7, code: expect.any(String) }),
    )
    await expect(api.replaceWeeklyAvailability([{
      dayOfWeek: 1,
      start: '09:00:00',
      end: '13:00:00',
      active: true,
    }])).resolves.toEqual([
      expect.objectContaining({ id: expect.any(Number), dayOfWeek: 1 }),
    ])
    await expect(api.createBlock({
      startsAt: '2026-08-15T00:00:00-04:00',
      endsAt: '2026-08-16T00:00:00-04:00',
      reason: null,
    })).resolves.toEqual(expect.objectContaining({
      id: expect.any(Number),
      createdAt: expect.any(String),
    }))
    await expect(api.readNotification(1)).resolves.toEqual(expect.objectContaining({
      id: expect.any(Number),
      unread: false,
    }))

    await expect(api.archiveService(7)).resolves.toBeUndefined()
    await expect(api.deleteBlock(1)).resolves.toBeUndefined()
  })
})

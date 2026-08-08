import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './http'
import { bookingApi } from './bookingApi'

vi.mock('./http', () => ({
  apiRequest: vi.fn(),
}))

describe('bookingApi', () => {
  beforeEach(() => {
    apiRequest.mockReset()
    apiRequest.mockResolvedValue({ ok: true })
  })

  it('loads the authenticated client pets from the accepted route', async () => {
    await bookingApi.pets()

    expect(apiRequest).toHaveBeenCalledWith('/api/v1/me/pets')
  })

  it('loads the public active service catalog from the accepted route', async () => {
    await bookingApi.services()

    expect(apiRequest).toHaveBeenCalledWith('/api/v1/services')
  })

  it('encodes inclusive clinic-local availability dates without changing them', async () => {
    await bookingApi.availability('2026-08-10', '2026-11-08')

    expect(apiRequest).toHaveBeenCalledWith(
      '/api/v1/availability?from=2026-08-10&to=2026-11-08',
    )
  })

  it('encodes unexpected query characters rather than interpolating them raw', async () => {
    await bookingApi.availability('2026-08-10&admin=true', '2026-08-12#later')

    expect(apiRequest).toHaveBeenCalledWith(
      '/api/v1/availability?from=2026-08-10%26admin%3Dtrue&to=2026-08-12%23later',
    )
  })

  it('creates exactly the accepted multi-pet payload and normalizes an empty note to null', async () => {
    const draft = {
      startsAt: '2026-08-10T10:00:00-04:00',
      items: [
        { petId: 84, serviceId: 7, petName: 'must not leak' },
        { petId: 85, serviceId: 9, serviceName: 'must not leak' },
      ],
      note: '',
      clientId: 12,
      status: 'CONFIRMED',
      veterinarian: 'Dra. AmiDog',
    }

    await bookingApi.createReservation(draft)

    expect(apiRequest).toHaveBeenCalledWith('/api/v1/me/reservations', {
      method: 'POST',
      body: {
        startsAt: '2026-08-10T10:00:00-04:00',
        items: [
          { petId: 84, serviceId: 7 },
          { petId: 85, serviceId: 9 },
        ],
        note: null,
      },
    })
  })

  it('preserves a normalized note and propagates the shared API failure unchanged', async () => {
    const failure = new Error('closed failure')
    apiRequest.mockRejectedValueOnce(failure)

    await expect(bookingApi.createReservation({
      startsAt: '2026-08-10T10:00:00-04:00',
      items: [{ petId: 84, serviceId: 7 }],
      note: 'Control anual',
    })).rejects.toBe(failure)
  })
})

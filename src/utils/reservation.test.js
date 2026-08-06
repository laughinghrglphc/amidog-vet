import { describe, expect, it } from 'vitest'
import {
  bookingAvailabilityRange,
  clinicDateValue,
  groupAvailabilitySlots,
} from './reservation'

describe('booking date utilities', () => {
  it('derives clinic-local dates at Chilean winter and summer UTC boundaries', () => {
    expect(clinicDateValue('2026-07-14T03:30:00Z')).toBe('2026-07-13')
    expect(clinicDateValue('2026-01-13T02:30:00Z')).toBe('2026-01-12')
  })

  it('builds a bounded inclusive 90-day clinic-local range', () => {
    expect(bookingAvailabilityRange(new Date('2026-08-10T12:00:00Z')))
      .toEqual({ from: '2026-08-10', to: '2026-11-07' })
  })

  it('sorts and groups only valid server slots without reconstructing startsAt', () => {
    expect(groupAvailabilitySlots([
      {
        startsAt: '2026-08-10T11:00:00-04:00',
        endsAt: '2026-08-10T11:30:00-04:00',
      },
      {
        startsAt: 'invalid',
        endsAt: 'invalid',
      },
      {
        startsAt: '2026-08-10T10:00:00-04:00',
        endsAt: '2026-08-10T10:30:00-04:00',
      },
    ])).toEqual([{
      date: '10-08-2026',
      dateValue: '2026-08-10',
      slots: [
        {
          startsAt: '2026-08-10T10:00:00-04:00',
          endsAt: '2026-08-10T10:30:00-04:00',
          time: '10:00',
        },
        {
          startsAt: '2026-08-10T11:00:00-04:00',
          endsAt: '2026-08-10T11:30:00-04:00',
          time: '11:00',
        },
      ],
    }])
  })
})

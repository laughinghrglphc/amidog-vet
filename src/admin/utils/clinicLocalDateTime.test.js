import { describe, expect, it } from 'vitest'
import {
  clinicDateRange,
  clinicLocalDateTimeToOffset,
} from './clinicLocalDateTime'

describe('clinic-local administrator block timestamps', () => {
  it.each([
    ['winter partial block', '2026-08-10T10:00', '2026-08-10T10:00:00-04:00'],
    ['summer partial block', '2026-01-10T10:00', '2026-01-10T10:00:00-03:00'],
  ])('uses the real America/Santiago offset for a %s', (_, local, expected) => {
    expect(clinicLocalDateTimeToOffset(local)).toBe(expected)
  })

  it('creates a full clinic-local day without parsing through the host timezone', () => {
    expect(clinicDateRange('2026-08-10')).toEqual({
      startsAt: '2026-08-10T00:00:00-04:00',
      endsAt: '2026-08-11T00:00:00-04:00',
    })
  })

  it.each([
    ['day before spring transition', '2026-09-05', {
      startsAt: '2026-09-05T00:00:00-04:00',
      endsAt: '2026-09-06T01:00:00-03:00',
    }],
    ['spring transition day', '2026-09-06', {
      startsAt: '2026-09-06T01:00:00-03:00',
      endsAt: '2026-09-07T00:00:00-03:00',
    }],
  ])('advances a nonexistent midnight to the first valid instant for the %s', (
    _,
    date,
    expected,
  ) => {
    expect(clinicDateRange(date)).toEqual(expected)
  })

  it('selects the earlier offset for an ambiguous fall wall time', () => {
    expect(clinicLocalDateTimeToOffset('2026-04-04T23:00'))
      .toBe('2026-04-04T23:00:00-03:00')
  })

  it('continues rejecting a nonexistent partial wall time', () => {
    expect(() => clinicLocalDateTimeToOffset('2026-09-06T00:00'))
      .toThrow('La hora local no existe')
  })
})

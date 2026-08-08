import { describe, expect, it } from 'vitest'
import { clinicTodayValue } from '../../../utils/petDates'

describe('administrator pet clinic date', () => {
  it('uses the Santiago civil day for the birthdate maximum', () => {
    expect(clinicTodayValue(
      new Date('2026-08-01T02:30:00Z'),
    )).toBe('2026-07-31')
  })
})

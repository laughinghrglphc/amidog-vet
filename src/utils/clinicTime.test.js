import { describe, expect, it } from 'vitest'
import {
  CLINIC_TIME_ZONE,
  formatClinicDateTime,
} from './clinicTime'

describe('formatClinicDateTime', () => {
  it('formats winter API timestamps with Chilean standard time', () => {
    expect(formatClinicDateTime('2026-07-13T13:00:00Z'))
      .toEqual({ date: '13-07-2026', time: '09:00' })
  })

  it('formats summer API timestamps with Chilean daylight time', () => {
    expect(formatClinicDateTime('2026-01-13T13:00:00Z'))
      .toEqual({ date: '13-01-2026', time: '10:00' })
  })

  it('uses America/Santiago even when the timestamp carries another offset', () => {
    expect(formatClinicDateTime('2026-07-14T01:30:00+02:00'))
      .toEqual({ date: '13-07-2026', time: '19:30' })
    expect(CLINIC_TIME_ZONE).toBe('America/Santiago')
  })

  it.each([undefined, null, '', 'not-a-timestamp'])(
    'returns a stable empty result for invalid input %s',
    (value) => {
      expect(formatClinicDateTime(value)).toEqual({ date: '', time: '' })
    },
  )
})

import { describe, expect, it } from 'vitest'
import {
  formatAppointmentDate,
  formatDashboardDate,
  formatScheduleTime,
} from './date'

describe('administrator Chilean date formatting', () => {
  it('uses America/Santiago when the host date is on another calendar day', () => {
    const instant = new Date('2026-07-29T02:00:00Z')

    expect(formatDashboardDate(instant)).toBe('Martes, 28 de julio')
  })

  it('labels an appointment relative to the clinic calendar day', () => {
    const now = new Date('2026-07-29T02:00:00Z')

    expect(formatAppointmentDate('2026-07-29T10:15:00-04:00', now)).toBe('Mañana, 10:15')
  })

  it('formats schedule times in Chilean clinic time', () => {
    expect(formatScheduleTime('2026-07-28T15:30:00-04:00')).toBe('15:30')
  })
})

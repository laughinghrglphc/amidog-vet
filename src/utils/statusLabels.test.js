import { describe, expect, it } from 'vitest'
import { STATUS_LABELS, statusLabel } from './statusLabels'

describe('reservation status labels', () => {
  it('maps every backend status code to its Spanish display label', () => {
    expect(STATUS_LABELS).toEqual({
      PENDING: 'Pendiente',
      CONFIRMED: 'Confirmada',
      CANCELLED: 'Cancelada',
      COMPLETED: 'Completada',
      NO_SHOW: 'No asistió',
    })
  })

  it('keeps unknown backend codes visible without changing them', () => {
    expect(statusLabel('FUTURE_STATUS')).toBe('FUTURE_STATUS')
    expect(statusLabel(null)).toBe('')
  })
})

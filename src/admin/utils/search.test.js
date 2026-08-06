import { describe, expect, it } from 'vitest'
import { findDashboardResults, normalizeForSearch } from './search'

const appointments = [
  {
    id: 42,
    clientName: 'María González',
    items: [
      {
        petName: 'Moka',
        species: 'Gato',
        serviceName: 'Consulta general',
      },
    ],
  },
]

describe('dashboard search', () => {
  it('normalizes accents and Chilean casing', () => {
    expect(normalizeForSearch('  MARÍA  ')).toBe('maria')
  })

  it('returns live appointment results without a messages collection', () => {
    expect(findDashboardResults('moka', { appointments })).toEqual([
      {
        id: 42,
        kind: 'appointment',
        eyebrow: 'Reserva',
        title: 'Moka · María González',
        detail: 'Consulta general',
      },
    ])
  })

  it('requires two normalized characters before searching', () => {
    expect(findDashboardResults('m', { appointments })).toEqual([])
  })
})

import { describe, expect, it } from 'vitest'
import { clinicPetAgeLabel } from '../../utils/petDates'

describe('PetCard clinic-local age', () => {
  it('does not advance age while the birthday is still tomorrow in Santiago', () => {
    expect(clinicPetAgeLabel(
      '2020-08-01',
      new Date('2026-08-01T02:30:00Z'),
    )).toBe('5 años')
  })

  it('advances age once the clinic civil date reaches the birthday', () => {
    expect(clinicPetAgeLabel(
      '2020-08-01',
      new Date('2026-08-01T04:30:00Z'),
    )).toBe('6 años')
  })
})

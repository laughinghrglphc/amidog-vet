import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  BOOKING_DRAFT_KEY,
  clearPersistedBookingDraft,
  clearPersistedBookingStart,
  LEGACY_BOOKING_DRAFT_KEY,
  useBookingDraft,
} from './useBookingDraft'

const validDraft = {
  items: [
    { petId: 84, serviceId: 7 },
    { petId: 85, serviceId: 9 },
  ],
  startsAt: '2026-08-10T10:00:00-04:00',
  note: 'Control anual',
}

beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
})

describe('useBookingDraft', () => {
  it('loads a valid versioned safe draft without using localStorage', () => {
    sessionStorage.setItem(BOOKING_DRAFT_KEY, JSON.stringify({
      version: 1,
      draft: validDraft,
    }))
    const localRead = vi.spyOn(localStorage, 'getItem')

    const { result } = renderHook(() => useBookingDraft())

    expect(result.current.draft).toEqual(validDraft)
    expect(localRead).not.toHaveBeenCalled()
  })

  it('clears the legacy PII draft instead of migrating any field', () => {
    sessionStorage.setItem(LEGACY_BOOKING_DRAFT_KEY, JSON.stringify({
      nombre: 'Ana Pérez',
      correo: 'ana@example.cl',
      telefono: '+56912345678',
      nombreMascota: 'Milo',
    }))

    const { result } = renderHook(() => useBookingDraft())

    expect(sessionStorage.getItem(LEGACY_BOOKING_DRAFT_KEY)).toBeNull()
    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).toBeNull()
    expect(result.current.draft).toEqual({
      items: [],
      startsAt: '',
      note: '',
    })
  })

  it.each([
    ['malformed JSON', '{not-json'],
    ['wrong version', JSON.stringify({ version: 2, draft: validDraft })],
    ['an array root', JSON.stringify([])],
  ])('ignores %s without crashing', (_label, stored) => {
    sessionStorage.setItem(BOOKING_DRAFT_KEY, stored)

    const { result } = renderHook(() => useBookingDraft())

    expect(result.current.draft).toEqual({
      items: [],
      startsAt: '',
      note: '',
    })
  })

  it('sanitizes IDs, duplicate pets, item count, note length, timestamp, and PII', () => {
    sessionStorage.setItem(BOOKING_DRAFT_KEY, JSON.stringify({
      version: 1,
      draft: {
        items: [
          { petId: '84', serviceId: '7', petName: 'Milo' },
          { petId: 84, serviceId: 9 },
          { petId: -1, serviceId: 7 },
          ...Array.from({ length: 12 }, (_, index) => ({
            petId: index + 100,
            serviceId: 9,
          })),
        ],
        startsAt: 'not-an-offset',
        note: `  ${'a'.repeat(550)}  `,
        email: 'ana@example.cl',
        token: 'secret',
        status: 'PENDING',
      },
    }))

    const { result } = renderHook(() => useBookingDraft())

    expect(result.current.draft.items).toHaveLength(10)
    expect(result.current.draft.items[0]).toEqual({ petId: 84, serviceId: 7 })
    expect(result.current.draft.startsAt).toBe('')
    expect(result.current.draft.note).toHaveLength(500)
    expect(result.current.draft).not.toHaveProperty('email')
    expect(result.current.draft).not.toHaveProperty('token')
    expect(result.current.draft).not.toHaveProperty('status')
  })

  it('tolerates denied storage reads, writes, and removal', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota', 'QuotaExceededError')
    })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })

    const { result } = renderHook(() => useBookingDraft())

    expect(() => {
      act(() => result.current.save(validDraft))
      act(() => result.current.clearStart())
      act(() => result.current.clear())
    }).not.toThrow()
  })

  it('persists only the versioned allow-listed shape', () => {
    const { result } = renderHook(() => useBookingDraft())

    act(() => result.current.save({
      ...validDraft,
      name: 'Ana Pérez',
      email: 'ana@example.cl',
      phone: '+56912345678',
      petNames: ['Milo', 'Luna'],
      serviceNames: ['Consulta', 'Vacuna'],
      apiResponse: { id: 301 },
      errors: { startsAt: 'bad' },
    }))

    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY))).toEqual({
      version: 1,
      draft: validDraft,
    })
  })

  it('reconciles items against active pets and services and reports repair', () => {
    sessionStorage.setItem(BOOKING_DRAFT_KEY, JSON.stringify({
      version: 1,
      draft: validDraft,
    }))
    const { result } = renderHook(() => useBookingDraft())

    let reconciliation
    act(() => {
      reconciliation = result.current.reconcile(
        [{ id: 84, active: true }, { id: 85, active: false }],
        [{ id: 7, active: true }, { id: 9, active: false }],
      )
    })

    expect(reconciliation).toEqual({
      changed: true,
      draft: {
        items: [{ petId: 84, serviceId: 7 }],
        startsAt: '2026-08-10T10:00:00-04:00',
        note: 'Control anual',
      },
    })
    expect(result.current.draft.items).toEqual([{ petId: 84, serviceId: 7 }])
  })

  it('clears only the occupied start after a collision', () => {
    const { result } = renderHook(() => useBookingDraft())
    act(() => result.current.save(validDraft))

    act(() => result.current.clearStart())

    expect(result.current.draft).toEqual({
      items: validDraft.items,
      startsAt: '',
      note: 'Control anual',
    })
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft)
      .toEqual(result.current.draft)
  })

  it('clears the safe draft only when explicitly requested', () => {
    const { result } = renderHook(() => useBookingDraft())
    act(() => result.current.save(validDraft))

    act(() => result.current.clear())

    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).toBeNull()
    expect(result.current.draft).toEqual({
      items: [],
      startsAt: '',
      note: '',
    })
  })

  it('durably clears the current safe draft after a successful outcome', () => {
    const newerDraft = {
      items: [{ petId: 84, serviceId: 9 }],
      startsAt: '2026-08-11T11:30:00-04:00',
      note: 'Nueva solicitud',
    }
    sessionStorage.setItem(BOOKING_DRAFT_KEY, JSON.stringify({
      version: 1,
      draft: newerDraft,
    }))
    sessionStorage.setItem(LEGACY_BOOKING_DRAFT_KEY, '{"private":"legacy"}')

    clearPersistedBookingDraft()

    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).toBeNull()
    expect(sessionStorage.getItem(LEGACY_BOOKING_DRAFT_KEY)).toBeNull()
  })

  it('does not let an older collision clear a newer selected start', () => {
    const newerDraft = {
      items: validDraft.items,
      startsAt: '2026-08-11T11:30:00-04:00',
      note: 'Nueva solicitud',
    }
    sessionStorage.setItem(BOOKING_DRAFT_KEY, JSON.stringify({
      version: 1,
      draft: newerDraft,
    }))

    expect(clearPersistedBookingStart(
      validDraft.startsAt,
      validDraft,
    )).toEqual(newerDraft)
    expect(JSON.parse(sessionStorage.getItem(BOOKING_DRAFT_KEY)).draft)
      .toEqual(newerDraft)
  })
})

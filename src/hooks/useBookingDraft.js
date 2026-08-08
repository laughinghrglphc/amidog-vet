import { useCallback, useState } from 'react'

export const BOOKING_DRAFT_KEY = 'amidogBookingDraft'
export const LEGACY_BOOKING_DRAFT_KEY = 'amidogReservaPendiente'

const DRAFT_VERSION = 1
const MAX_ITEMS = 10
const MAX_NOTE_LENGTH = 500
const EMPTY_DRAFT = Object.freeze({
  items: [],
  startsAt: '',
  note: '',
})

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function positiveInteger(value) {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : null
}

function offsetTimestamp(value) {
  if (typeof value !== 'string' || value === '') {
    return ''
  }
  const timestamp = value.trim()
  const hasOffset = /T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:\d{2})$/.test(
    timestamp,
  )
  return hasOffset && Number.isFinite(Date.parse(timestamp)) ? timestamp : ''
}

function safeStorage() {
  try {
    return globalThis.sessionStorage
  } catch {
    return null
  }
}

function removeStored(key) {
  try {
    safeStorage()?.removeItem(key)
  } catch {
    // Storage can be unavailable, denied, or over quota.
  }
}

function persistDraft(draft) {
  try {
    safeStorage()?.setItem(BOOKING_DRAFT_KEY, JSON.stringify({
      version: DRAFT_VERSION,
      draft,
    }))
  } catch {
    // The in-memory draft remains usable when storage is unavailable.
  }
}

function readPersistedDraft() {
  try {
    const stored = safeStorage()?.getItem(BOOKING_DRAFT_KEY)
    if (!stored) {
      return null
    }
    const parsed = JSON.parse(stored)
    if (!isRecord(parsed)
      || parsed.version !== DRAFT_VERSION
      || !isRecord(parsed.draft)) {
      return null
    }
    return sanitizeBookingDraft(parsed.draft)
  } catch {
    return null
  }
}

export function sanitizeBookingDraft(value) {
  const source = isRecord(value) ? value : {}
  const seenPets = new Set()
  const items = []

  if (Array.isArray(source.items)) {
    for (const item of source.items) {
      if (items.length === MAX_ITEMS) {
        break
      }
      if (!isRecord(item)) {
        continue
      }
      const petId = positiveInteger(item.petId)
      const serviceId = positiveInteger(item.serviceId)
      if (!petId || !serviceId || seenPets.has(petId)) {
        continue
      }
      seenPets.add(petId)
      items.push({ petId, serviceId })
    }
  }

  const note = typeof source.note === 'string'
    ? source.note.trim().slice(0, MAX_NOTE_LENGTH)
    : ''

  return {
    items,
    startsAt: offsetTimestamp(source.startsAt),
    note,
  }
}

export function clearPersistedBookingDraft() {
  removeStored(BOOKING_DRAFT_KEY)
  removeStored(LEGACY_BOOKING_DRAFT_KEY)
}

export function clearPersistedBookingStart(expectedStart, fallbackDraft) {
  const current = readPersistedDraft() ?? sanitizeBookingDraft(fallbackDraft)
  const expected = offsetTimestamp(expectedStart)
  if (expected && current.startsAt !== expected) {
    return current
  }
  const cleared = { ...current, startsAt: '' }
  persistDraft(cleared)
  return cleared
}

function loadDraft() {
  removeStored(LEGACY_BOOKING_DRAFT_KEY)

  try {
    const stored = safeStorage()?.getItem(BOOKING_DRAFT_KEY)
    if (!stored) {
      return { ...EMPTY_DRAFT, items: [] }
    }
    const parsed = JSON.parse(stored)
    if (!isRecord(parsed)
      || parsed.version !== DRAFT_VERSION
      || !isRecord(parsed.draft)) {
      removeStored(BOOKING_DRAFT_KEY)
      return { ...EMPTY_DRAFT, items: [] }
    }
    const sanitized = sanitizeBookingDraft(parsed.draft)
    persistDraft(sanitized)
    return sanitized
  } catch {
    removeStored(BOOKING_DRAFT_KEY)
    return { ...EMPTY_DRAFT, items: [] }
  }
}

export function useBookingDraft() {
  const [draft, setDraft] = useState(loadDraft)

  const save = useCallback((nextDraft) => {
    const sanitized = sanitizeBookingDraft(nextDraft)
    setDraft(sanitized)
    persistDraft(sanitized)
    return sanitized
  }, [])

  const clear = useCallback(() => {
    const empty = { ...EMPTY_DRAFT, items: [] }
    setDraft(empty)
    clearPersistedBookingDraft()
  }, [])

  const clearStart = useCallback(() => {
    let cleared
    setDraft((current) => {
      cleared = { ...current, startsAt: '' }
      persistDraft(cleared)
      return cleared
    })
    return cleared
  }, [])

  const reconcile = useCallback((pets, services) => {
    const activePets = new Set(
      (Array.isArray(pets) ? pets : [])
        .filter((pet) => pet?.active !== false)
        .map((pet) => positiveInteger(pet?.id))
        .filter(Boolean),
    )
    const activeServices = new Set(
      (Array.isArray(services) ? services : [])
        .filter((service) => service?.active !== false)
        .map((service) => positiveInteger(service?.id))
        .filter(Boolean),
    )
    const items = draft.items.filter(({ petId, serviceId }) => (
      activePets.has(petId) && activeServices.has(serviceId)
    ))
    const changed = items.length !== draft.items.length
    const nextDraft = changed ? { ...draft, items } : draft
    if (changed) {
      setDraft(nextDraft)
      persistDraft(nextDraft)
    }
    return { changed, draft: nextDraft }
  }, [draft])

  return {
    clear,
    clearStart,
    draft,
    reconcile,
    save,
  }
}

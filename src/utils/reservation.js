import { CLINIC_TIME_ZONE, formatClinicDateTime } from './clinicTime'

const clinicDateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: CLINIC_TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

function validDate(value) {
  const date = value instanceof Date ? new Date(value.getTime()) : new Date(value)
  return Number.isFinite(date.getTime()) ? date : null
}

export function clinicDateValue(value) {
  const date = validDate(value)
  if (!date) {
    return ''
  }
  const parts = clinicDateFormatter.formatToParts(date)
  const part = (type) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}

function addCalendarDays(dateValue, days) {
  const [year, month, day] = dateValue.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day + days))
  return [
    date.getUTCFullYear(),
    String(date.getUTCMonth() + 1).padStart(2, '0'),
    String(date.getUTCDate()).padStart(2, '0'),
  ].join('-')
}

export function bookingAvailabilityRange(now = new Date(), days = 90) {
  const from = clinicDateValue(now)
  const safeDays = Number.isInteger(days) && days > 0 ? days : 90
  return {
    from,
    to: addCalendarDays(from, safeDays - 1),
  }
}

export function groupAvailabilitySlots(value) {
  const slots = (Array.isArray(value) ? value : [])
    .filter((slot) => (
      slot
      && typeof slot.startsAt === 'string'
      && Number.isFinite(Date.parse(slot.startsAt))
    ))
    .sort((left, right) => Date.parse(left.startsAt) - Date.parse(right.startsAt))

  const groups = new Map()
  for (const slot of slots) {
    const formatted = formatClinicDateTime(slot.startsAt)
    const dateValue = clinicDateValue(slot.startsAt)
    if (!formatted.date || !formatted.time || !dateValue) {
      continue
    }
    if (!groups.has(dateValue)) {
      groups.set(dateValue, {
        date: formatted.date,
        dateValue,
        slots: [],
      })
    }
    groups.get(dateValue).slots.push({
      startsAt: slot.startsAt,
      endsAt: slot.endsAt,
      time: formatted.time,
    })
  }
  return Array.from(groups.values())
}

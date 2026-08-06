import { APP_TIME_ZONE } from './date'

const LOCAL_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/

const formatter = new Intl.DateTimeFormat('en-CA', {
  day: '2-digit',
  hour: '2-digit',
  hourCycle: 'h23',
  minute: '2-digit',
  month: '2-digit',
  second: '2-digit',
  timeZone: APP_TIME_ZONE,
  year: 'numeric',
})

function partsAt(instant) {
  return Object.fromEntries(
    formatter.formatToParts(instant).map(({ type, value }) => [type, value]),
  )
}

function offsetMinutesAt(instant) {
  const parts = partsAt(instant)
  const zonedAsUtc = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    Number(parts.hour),
    Number(parts.minute),
    Number(parts.second),
  )
  return Math.round((zonedAsUtc - instant.getTime()) / 60_000)
}

function offsetText(minutes) {
  const sign = minutes < 0 ? '-' : '+'
  const absolute = Math.abs(minutes)
  return `${sign}${String(Math.floor(absolute / 60)).padStart(2, '0')}:${String(absolute % 60).padStart(2, '0')}`
}

export function clinicLocalDateTimeToOffset(value) {
  const match = LOCAL_PATTERN.exec(value)
  if (!match) throw new TypeError('La fecha y hora local no es válida.')
  const [, year, month, day, hour, minute] = match
  const localAsUtc = Date.UTC(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
  )

  let offset = offsetMinutesAt(new Date(localAsUtc))
  let instant = new Date(localAsUtc - offset * 60_000)
  offset = offsetMinutesAt(instant)
  instant = new Date(localAsUtc - offset * 60_000)
  const actual = partsAt(instant)
  if (
    actual.year !== year
    || actual.month !== month
    || actual.day !== day
    || actual.hour !== hour
    || actual.minute !== minute
  ) {
    throw new TypeError('La hora local no existe en America/Santiago.')
  }

  return `${value}:00${offsetText(offset)}`
}

function clinicStartOfDayToOffset(value) {
  const match = DATE_PATTERN.exec(value)
  if (!match) throw new TypeError('La fecha local no es válida.')
  const [, year, month, day] = match
  const localDate = new Date(Date.UTC(
    Number(year),
    Number(month) - 1,
    Number(day),
  ))
  const normalizedDate = [
    localDate.getUTCFullYear(),
    String(localDate.getUTCMonth() + 1).padStart(2, '0'),
    String(localDate.getUTCDate()).padStart(2, '0'),
  ].join('-')
  if (normalizedDate !== value) {
    throw new TypeError('La fecha local no es válida.')
  }

  for (let minute = 0; minute < 180; minute += 1) {
    const candidateInstant = new Date(localDate.getTime() + minute * 60_000)
    const candidate = candidateInstant.toISOString().slice(0, 16)
    try {
      return clinicLocalDateTimeToOffset(candidate)
    } catch (error) {
      if (
        !(error instanceof TypeError)
        || !error.message.includes('no existe')
      ) {
        throw error
      }
    }
  }

  throw new TypeError('El día local no tiene un instante de inicio válido.')
}

export function clinicDateRange(value) {
  const match = DATE_PATTERN.exec(value)
  if (!match) throw new TypeError('La fecha local no es válida.')
  const [, year, month, day] = match
  const next = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day) + 1))
  const nextDate = [
    next.getUTCFullYear(),
    String(next.getUTCMonth() + 1).padStart(2, '0'),
    String(next.getUTCDate()).padStart(2, '0'),
  ].join('-')

  return {
    startsAt: clinicStartOfDayToOffset(value),
    endsAt: clinicStartOfDayToOffset(nextDate),
  }
}

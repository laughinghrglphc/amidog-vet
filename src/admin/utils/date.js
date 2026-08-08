export const APP_TIME_ZONE = 'America/Santiago'

const calendarParts = (date) => {
  const parts = new Intl.DateTimeFormat('en-CA', {
    day: '2-digit',
    month: '2-digit',
    timeZone: APP_TIME_ZONE,
    year: 'numeric',
  }).formatToParts(date)

  return Object.fromEntries(parts.map(({ type, value }) => [type, value]))
}

const calendarDayNumber = (date) => {
  const { day, month, year } = calendarParts(date)
  return Date.UTC(Number(year), Number(month) - 1, Number(day))
}

export function clinicCalendarDate(date = new Date()) {
  const { day, month, year } = calendarParts(date)
  return new Date(Number(year), Number(month) - 1, Number(day))
}

export function formatScheduleTime(startsAt) {
  return new Intl.DateTimeFormat('es-CL', {
    hour: '2-digit',
    hourCycle: 'h23',
    minute: '2-digit',
    timeZone: APP_TIME_ZONE,
  }).format(new Date(startsAt))
}

export function formatClinicDateTime(value) {
  return new Intl.DateTimeFormat('es-CL', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: APP_TIME_ZONE,
  }).format(new Date(value))
}

export function formatDashboardDate(date = new Date()) {
  const formatted = new Intl.DateTimeFormat('es-CL', {
    day: 'numeric',
    month: 'long',
    timeZone: APP_TIME_ZONE,
    weekday: 'long',
  }).format(date)

  return formatted.charAt(0).toLocaleUpperCase('es-CL') + formatted.slice(1)
}

export function formatAppointmentDate(startsAt, now = new Date()) {
  const appointmentDate = new Date(startsAt)
  const dayDifference = Math.round(
    (calendarDayNumber(appointmentDate) - calendarDayNumber(now)) / 86_400_000,
  )
  const time = formatScheduleTime(startsAt)

  if (dayDifference === 0) return `Hoy, ${time}`
  if (dayDifference === 1) return `Mañana, ${time}`

  const dateParts = new Intl.DateTimeFormat('es-CL', {
    day: 'numeric',
    month: 'short',
    timeZone: APP_TIME_ZONE,
  }).formatToParts(appointmentDate)
  const day = dateParts.find((part) => part.type === 'day').value
  const month = dateParts
    .find((part) => part.type === 'month')
    .value.replace('.', '')

  return `${day} ${month}, ${time}`
}

export const CLINIC_TIME_ZONE = 'America/Santiago'

const clinicDateTimeFormatter = new Intl.DateTimeFormat('es-CL', {
  timeZone: CLINIC_TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function validDate(value) {
  if (value === undefined || value === null || value === '') {
    return null
  }
  const date = value instanceof Date
    ? new Date(value.getTime())
    : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatClinicDateTime(value) {
  const date = validDate(value)
  if (!date) {
    return { date: '', time: '' }
  }

  const parts = clinicDateTimeFormatter.formatToParts(date)
  const part = (type) => parts.find((item) => item.type === type)?.value ?? ''
  return {
    date: `${part('day')}-${part('month')}-${part('year')}`,
    time: `${part('hour')}:${part('minute')}`,
  }
}

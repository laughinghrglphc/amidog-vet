const dateFormatter = new Intl.DateTimeFormat('es-CL', {
  day: '2-digit',
  month: 'long',
  year: 'numeric'
})

const shortDateFormatter = new Intl.DateTimeFormat('es-CL', {
  day: 'numeric',
  month: 'long'
})

export function formatDate(date) {
  if (!date) return ''
  return dateFormatter.format(new Date(`${date}T12:00:00`))
}

export function formatShortDate(date) {
  if (!date) return ''
  return shortDateFormatter.format(new Date(`${date}T12:00:00`))
}

export function todayValue() {
  const current = new Date()
  const offset = current.getTimezoneOffset()
  return new Date(current.getTime() - offset * 60000).toISOString().split('T')[0]
}

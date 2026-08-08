import { useMemo, useState } from 'react'

const WEEKDAYS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']
const fullDateFormatter = new Intl.DateTimeFormat('es-CL', {
  dateStyle: 'full',
  timeZone: 'UTC',
})
const monthFormatter = new Intl.DateTimeFormat('es-CL', {
  month: 'long',
  timeZone: 'UTC',
  year: 'numeric',
})

function parseDateKey(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '')
  if (!match) return null
  const [, year, month, day] = match
  return new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)))
}

function dateKey(year, month, day) {
  return [
    String(year).padStart(4, '0'),
    String(month + 1).padStart(2, '0'),
    String(day).padStart(2, '0'),
  ].join('-')
}

function monthValue(date) {
  return date.getUTCFullYear() * 12 + date.getUTCMonth()
}

function dateFromMonthValue(value, day = 1) {
  const year = Math.floor(value / 12)
  const month = value % 12
  return new Date(Date.UTC(year, month, day))
}

export default function BookingCalendar({
  availableDates,
  from,
  onSelectDate,
  selectedDate,
  to,
}) {
  const available = useMemo(
    () => new Set(Array.isArray(availableDates) ? availableDates : []),
    [availableDates],
  )
  const initialDate = parseDateKey(selectedDate)
    ?? parseDateKey(availableDates?.[0])
    ?? parseDateKey(from)
    ?? new Date()
  const [visibleMonth, setVisibleMonth] = useState(() => monthValue(initialDate))
  const firstMonth = monthValue(parseDateKey(from) ?? initialDate)
  const lastMonth = monthValue(parseDateKey(to) ?? initialDate)

  const firstOfMonth = dateFromMonthValue(visibleMonth)
  const year = firstOfMonth.getUTCFullYear()
  const month = firstOfMonth.getUTCMonth()
  const leadingDays = (firstOfMonth.getUTCDay() + 6) % 7
  const daysInMonth = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  const trailingDays = (7 - ((leadingDays + daysInMonth) % 7)) % 7

  return (
    <section className="calendario" aria-label="Calendario de disponibilidad">
      <div className="calendario__cabecera">
        <h3 className="calendario__mes">
          {monthFormatter.format(firstOfMonth)}
        </h3>
        <div className="calendario__nav">
          <button
            type="button"
            className="btn btn--circulo"
            aria-label="Mes anterior"
            disabled={visibleMonth <= firstMonth}
            onClick={() => setVisibleMonth((current) => current - 1)}
          >
            ‹
          </button>
          <button
            type="button"
            className="btn btn--circulo"
            aria-label="Mes siguiente"
            disabled={visibleMonth >= lastMonth}
            onClick={() => setVisibleMonth((current) => current + 1)}
          >
            ›
          </button>
        </div>
      </div>
      <div className="calendario__dias-semana" aria-hidden="true">
        {WEEKDAYS.map((weekday) => <span key={weekday}>{weekday}</span>)}
      </div>
      <div className="calendario__dias">
        {Array.from({ length: leadingDays }, (_, index) => (
          <span className="dia-btn otro-mes" aria-hidden="true" key={`before-${index}`} />
        ))}
        {Array.from({ length: daysInMonth }, (_, index) => {
          const day = index + 1
          const key = dateKey(year, month, day)
          const date = new Date(Date.UTC(year, month, day))
          const inRange = key >= from && key <= to
          const isAvailable = inRange && available.has(key)
          const isSelected = key === selectedDate
          return (
            <button
              type="button"
              className={`dia-btn${isAvailable ? ' disponible' : ''}${
                isSelected ? ' seleccionado' : ''
              }`}
              aria-label={`${fullDateFormatter.format(date)}, ${
                isAvailable ? 'disponible' : 'sin horarios'
              }`}
              aria-pressed={isSelected}
              disabled={!isAvailable}
              key={key}
              onClick={() => onSelectDate(key)}
            >
              {day}
            </button>
          )
        })}
        {Array.from({ length: trailingDays }, (_, index) => (
          <span className="dia-btn otro-mes" aria-hidden="true" key={`after-${index}`} />
        ))}
      </div>
    </section>
  )
}

import { useMemo, useState } from 'react'
import { clinicCalendarDate } from '../../utils/date'
import Icon from '../ui/Icon'

const monthNames = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
]

const sameDay = (left, right) =>
  left?.getFullYear() === right?.getFullYear()
  && left?.getMonth() === right?.getMonth()
  && left?.getDate() === right?.getDate()

export default function MiniCalendar({ now, onOpenAgenda }) {
  const clinicToday = clinicCalendarDate(now)
  const [currentMonth, setCurrentMonth] = useState(
    () => new Date(clinicToday.getFullYear(), clinicToday.getMonth(), 1),
  )
  const [selectedDate, setSelectedDate] = useState(clinicToday)

  const calendarDays = useMemo(() => {
    const year = currentMonth.getFullYear()
    const month = currentMonth.getMonth()
    const firstDay = new Date(year, month, 1)
    const lastDay = new Date(year, month + 1, 0)
    const startOffset = (firstDay.getDay() + 6) % 7
    const previousMonthLastDay = new Date(year, month, 0).getDate()
    const cells = []

    for (let index = startOffset - 1; index >= 0; index -= 1) {
      cells.push({ day: previousMonthLastDay - index, muted: true, date: null })
    }
    for (let day = 1; day <= lastDay.getDate(); day += 1) {
      cells.push({ day, muted: false, date: new Date(year, month, day) })
    }
    let nextDay = 1
    while (cells.length < 42) {
      cells.push({ day: nextDay, muted: true, date: null })
      nextDay += 1
    }
    return cells
  }, [currentMonth])

  const changeMonth = (direction) => {
    const nextMonth = new Date(
      currentMonth.getFullYear(),
      currentMonth.getMonth() + direction,
      1,
    )
    setCurrentMonth(nextMonth)
    setSelectedDate(nextMonth)
  }

  return (
    <section className="admin-card admin-calendar-card" id="calendario">
      <header className="admin-card__header admin-calendar-card__header">
        <button type="button" aria-label="Mes anterior" onClick={() => changeMonth(-1)}>
          <Icon name="chevronLeft" size={16} />
        </button>
        <h3>{monthNames[currentMonth.getMonth()]} {currentMonth.getFullYear()}</h3>
        <button type="button" aria-label="Mes siguiente" onClick={() => changeMonth(1)}>
          <Icon name="chevronRight" size={16} />
        </button>
      </header>

      <div className="admin-calendar-card__weekdays" aria-hidden="true">
        {['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sá', 'Do'].map((day) => <span key={day}>{day}</span>)}
      </div>
      <div className="admin-calendar-card__days">
        {calendarDays.map((item, index) => (
          <button
            type="button"
            key={`${item.day}-${index}`}
            disabled={item.muted}
            className={[
              item.muted ? 'is-muted' : '',
              sameDay(item.date, selectedDate) ? 'is-selected' : '',
              sameDay(item.date, clinicToday) ? 'has-event' : '',
            ].filter(Boolean).join(' ')}
            onClick={() => setSelectedDate(item.date)}
            aria-label={item.date ? `${item.day} de ${monthNames[currentMonth.getMonth()]}` : undefined}
          >
            {item.day}
          </button>
        ))}
      </div>
      <button
        type="button"
        className="admin-calendar-card__selected"
        onClick={() => onOpenAgenda(selectedDate)}
      >
        Ver agenda del {selectedDate.toLocaleDateString('es-CL', { day: 'numeric', month: 'long' })}
      </button>
    </section>
  )
}

import { formatScheduleTime } from '../../utils/date'
import Icon from '../ui/Icon'

function scheduleSummary(item) {
  return {
    pets: item.items.map((entry) => entry.petName).join(', '),
    services: [...new Set(item.items.map((entry) => entry.serviceName))].join(', '),
  }
}

export default function AgendaCard({ onOpenAppointment, onViewCalendar, schedule }) {
  return (
    <article className="admin-card admin-agenda">
      <header className="admin-card__header">
        <h3><Icon name="calendar" size={17} /> Agenda de hoy</h3>
        <button type="button" onClick={onViewCalendar}>Ver calendario</button>
      </header>
      <div className="admin-agenda__list">
        {schedule.map((item) => {
          const summary = scheduleSummary(item)
          return (
            <button
              type="button"
              className="admin-agenda__item"
              key={item.reservationId}
              onClick={() => onOpenAppointment(item.reservationId)}
            >
              <time>{formatScheduleTime(item.startsAt)}</time>
              <i />
              <span><strong>{summary.services}</strong><small>{summary.pets}</small></span>
            </button>
          )
        })}
        {!schedule.length && <p className="admin-empty-cell">No hay atenciones para hoy.</p>}
      </div>
    </article>
  )
}

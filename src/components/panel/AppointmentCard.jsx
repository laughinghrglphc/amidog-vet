import { CloseCircleIcon } from './Icons'
import { formatClinicDateTime } from '../../utils/clinicTime'
import { statusLabel } from '../../utils/statusLabels'

function AppointmentCard({
  reservation,
  onCancel,
  onReschedule,
  canCancel,
  canReschedule,
}) {
  const { date, time } = formatClinicDateTime(reservation.startsAt)
  const petNames = reservation.items.map((item) => item.petName).join(' y ')

  return (
    <article className="appointment-card">
      <div className="appointment-card__items">
        <h3>Reserva #{reservation.id}</h3>
        <ul>
          {reservation.items.map((item) => (
            <li key={item.petId}>
              <strong>{item.petName}</strong>
              <span>{item.serviceName}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="appointment-card__data">
        <span>Fecha</span>
        <p>{date}</p>
      </div>

      <div className="appointment-card__data">
        <span>Hora</span>
        <p>{time} hrs</p>
      </div>

      <div className="appointment-card__data">
        <span>Estado</span>
        <p><span className={`reservation-status reservation-status--${reservation.status.toLowerCase()}`}>
          {statusLabel(reservation.status)}
        </span></p>
      </div>

      {(canCancel || canReschedule) && (
        <div className="appointment-card__actions">
          {canReschedule && (
            <button
              className="cancel-button"
              type="button"
              aria-label={`Reprogramar reserva de ${petNames}`}
              onClick={() => onReschedule(reservation)}
            >
              Reprogramar
            </button>
          )}
          {canCancel && (
            <button
              className="cancel-button"
              type="button"
              aria-label={`Cancelar reserva de ${petNames}`}
              onClick={() => onCancel(reservation)}
            >
              <CloseCircleIcon />
              Cancelar reserva
            </button>
          )}
        </div>
      )}
    </article>
  )
}

export default AppointmentCard

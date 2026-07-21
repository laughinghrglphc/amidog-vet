import maxImage from '../../assets/panel/max.png'
import annieImage from '../../assets/panel/annie.png'
import { CloseCircleIcon } from './Icons'
import { formatDate } from '../../utils/panelDate'

const images = { max: maxImage, annie: annieImage }

function AppointmentCard({ appointment, onCancel }) {
  return (
    <article className="appointment-card">
      <div className="appointment-card__pet">
        <div className="pet-avatar">
          <img src={images[appointment.image] ?? maxImage} alt={appointment.pet} />
        </div>
        <div className="appointment-card__identity">
          <h3>{appointment.pet}</h3>
          <p>{appointment.kind}</p>
          <span className="pill">{appointment.tag}</span>
        </div>
      </div>

      <div className="appointment-card__data">
        <span>Servicio</span>
        <p>{appointment.service}</p>
      </div>

      <div className="appointment-card__data">
        <span>Veterinario/a</span>
        <p>{appointment.veterinarian}</p>
      </div>

      <div className="appointment-card__data">
        <span>Fecha</span>
        <p>{formatDate(appointment.date)}</p>
      </div>

      <div className="appointment-card__data appointment-card__time">
        <span>Hora</span>
        <p>{appointment.time} hrs</p>
      </div>

      <button className="cancel-button" type="button" onClick={() => onCancel(appointment)}>
        <CloseCircleIcon />
        Anular hora
      </button>
    </article>
  )
}

export default AppointmentCard

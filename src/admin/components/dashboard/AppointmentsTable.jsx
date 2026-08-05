import { useState } from 'react'
import { statusLabel } from '../../../utils/statusLabels'
import { imageRegistry } from '../../data/imageRegistry'
import { formatAppointmentDate } from '../../utils/date'
import { imageKeyForSpecies } from '../../utils/petImage'
import Icon from '../ui/Icon'

function appointmentDetails(appointment) {
  const firstItem = appointment.items[0]
  return {
    imageKey: imageKeyForSpecies(firstItem?.species),
    pet: firstItem?.petName ?? 'Mascota',
    service: [...new Set(appointment.items.map((item) => item.serviceName))].join(', '),
    species: firstItem?.species ?? '',
  }
}

export default function AppointmentsTable({
  appointments,
  now,
  onOpen,
  onViewAll,
}) {
  const [openMenuId, setOpenMenuId] = useState(null)

  return (
    <article className="admin-card admin-appointments">
      <header className="admin-card__header">
        <h3><Icon name="calendar" size={17} /> Próximas reservas</h3>
        <button type="button" onClick={onViewAll}>Ver reservas</button>
      </header>

      <div className="admin-appointments__scroll">
        <table className="admin-appointments__table">
          <thead>
            <tr>
              <th scope="col">Mascota</th>
              <th scope="col">Dueño</th>
              <th scope="col">Servicio</th>
              <th scope="col">Fecha y hora</th>
              <th scope="col">Estado</th>
              <th scope="col"><span className="sr-only">Acciones</span></th>
            </tr>
          </thead>
          <tbody>
            {appointments.map((appointment) => {
              const details = appointmentDetails(appointment)
              const label = statusLabel(appointment.status)
              return (
                <tr key={appointment.id}>
                  <td data-label="Mascota">
                    <span className="admin-pet-cell">
                      <img
                        src={imageRegistry[details.imageKey]}
                        alt=""
                        loading="lazy"
                      />
                      <span>
                        <strong>{details.pet}</strong>
                        <small>
                          {details.species}
                          {appointment.items.length > 1 ? ` · +${appointment.items.length - 1}` : ''}
                        </small>
                      </span>
                    </span>
                  </td>
                  <td data-label="Dueño">{appointment.clientName}</td>
                  <td data-label="Servicio">
                    <span className="admin-service-cell">
                      <i><Icon name="stethoscope" size={12} /></i>
                      {details.service}
                    </span>
                  </td>
                  <td data-label="Fecha y hora">{formatAppointmentDate(appointment.startsAt, now)}</td>
                  <td data-label="Estado">
                    <em className={appointment.status === 'CONFIRMED' ? 'is-confirmed' : 'is-pending'}>
                      {label}
                    </em>
                  </td>
                  <td>
                    <div className="admin-row-actions">
                      <button
                        type="button"
                        aria-controls={`appointment-menu-${appointment.id}`}
                        aria-expanded={openMenuId === appointment.id}
                        aria-label={`Opciones de ${details.pet}`}
                        onClick={() => setOpenMenuId((current) =>
                          current === appointment.id ? null : appointment.id)}
                      >
                        <Icon name="more" size={16} />
                      </button>
                      {openMenuId === appointment.id && (
                        <div id={`appointment-menu-${appointment.id}`} className="admin-row-menu">
                          <button
                            type="button"
                            onClick={() => {
                              onOpen(appointment.id)
                              setOpenMenuId(null)
                            }}
                          >
                            <Icon name="info" size={15} /> Ver detalle
                          </button>
                        </div>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })}
            {!appointments.length && (
              <tr><td colSpan="6" className="admin-empty-cell">No hay reservas próximas.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </article>
  )
}

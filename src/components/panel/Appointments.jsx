import calendar from '../../assets/panel/calendar.png'
import AppointmentCard from './AppointmentCard'

function Appointments({
  reservations,
  onCancel,
  onReschedule,
  onViewAll,
  onAdd,
  canCancel,
}) {
  return (
    <section className="panel panel--appointments" id="servicios">
      <div className="panel__header">
        <h2><img src={calendar} alt="" /> Mis reservas</h2>
        <button type="button" onClick={onViewAll}>Ver todas</button>
      </div>

      <div className="panel__body panel__body--appointments">
        {reservations.length > 0 ? reservations.map((reservation) => (
          <AppointmentCard
            key={reservation.id}
            reservation={reservation}
            onCancel={onCancel}
            onReschedule={onReschedule}
            canCancel={canCancel(reservation)}
            canReschedule={canCancel(reservation)}
          />
        )) : (
          <div className="empty-state">
            <img src={calendar} alt="" />
            <p>No tienes reservas registradas.</p>
          </div>
        )}
      </div>

      <button className="outline-action" type="button" onClick={onAdd}>
        <img src={calendar} alt="" />
        Agendar nueva hora
      </button>
    </section>
  )
}

export default Appointments

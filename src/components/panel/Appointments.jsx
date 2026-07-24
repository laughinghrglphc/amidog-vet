import calendar from '../../assets/panel/calendar.png'
import AppointmentCard from './AppointmentCard'

function Appointments({ appointments, onCancel, onViewAll, onAdd }) {
  const visibleAppointments = appointments.slice(0, 2)

  return (
    <section className="panel panel--appointments" id="servicios">
      <div className="panel__header">
        <h2><img src={calendar} alt="" /> Mis horas</h2>
        <button type="button" onClick={onViewAll}>Ver todas</button>
      </div>

      <div className="panel__body panel__body--appointments">
        {visibleAppointments.length > 0 ? visibleAppointments.map((appointment) => (
          <AppointmentCard key={appointment.id} appointment={appointment} onCancel={onCancel} />
        )) : (
          <div className="empty-state">
            <img src={calendar} alt="" />
            <p>No tienes horas agendadas.</p>
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

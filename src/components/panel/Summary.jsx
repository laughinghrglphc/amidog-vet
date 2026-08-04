import calendar from '../../assets/panel/calendar.png'
import clock from '../../assets/panel/clock.png'
import SummaryCard from './SummaryCard'
import { PawTrailIcon } from './Icons'
import { formatClinicDateTime } from '../../utils/clinicTime'

function Summary({ appointmentCount, petCount, nextAppointment, onViewAppointments, onViewPets }) {
  const next = formatClinicDateTime(nextAppointment?.startsAt)
  return (
    <section className="summary" aria-label="Resumen de la cuenta">
      <SummaryCard
        icon={<img src={calendar} alt="" />}
        title="Próxima hora"
        detail={nextAppointment ? next.date : 'Sin horas próximas'}
        value={nextAppointment ? `${next.time} hrs` : '—'}
        action={nextAppointment ? 'Ver reserva' : 'Revisar reservas'}
        variant="next"
        onAction={onViewAppointments}
      />
      <SummaryCard
        icon={<img src={clock} alt="" />}
        title="Reservas registradas"
        value={appointmentCount}
        action="Ver todas mis reservas"
        onAction={onViewAppointments}
      />
      <SummaryCard
        icon={<PawTrailIcon />}
        title="Mascotas registradas"
        value={petCount}
        action="Ver mis mascotas"
        onAction={onViewPets}
      />
    </section>
  )
}

export default Summary

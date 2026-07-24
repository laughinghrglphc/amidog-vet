import calendar from '../../assets/panel/calendar.png'
import clock from '../../assets/panel/clock.png'
import SummaryCard from './SummaryCard'
import { PawTrailIcon } from './Icons'
import { formatShortDate } from '../../utils/panelDate'

function Summary({ appointmentCount, petCount, nextAppointment, onViewAppointments, onViewPets }) {
  return (
    <section className="summary" aria-label="Resumen de la cuenta">
      <SummaryCard
        icon={<img src={calendar} alt="" />}
        title="Próxima hora"
        detail={nextAppointment ? formatShortDate(nextAppointment.date) : 'Sin horas próximas'}
        value={nextAppointment ? `${nextAppointment.time} hrs` : '—'}
        action={nextAppointment ? nextAppointment.service : 'Agendar una hora'}
        variant="next"
        onAction={onViewAppointments}
      />
      <SummaryCard
        icon={<img src={clock} alt="" />}
        title="Horas agendadas"
        value={appointmentCount}
        action="Ver todas mis horas"
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

import calendarIcon from '../../../assets/admin/calendar.webp'
import usersIcon from '../../../assets/admin/users.webp'
import Icon from '../ui/Icon'

const cards = [
  {
    detail: 'Actividad de la agenda',
    icon: calendarIcon,
    key: 'todayAppointments',
    label: 'Reservas de hoy',
    tone: 'teal',
  },
  {
    detail: 'Base de clientes',
    icon: usersIcon,
    key: 'clients',
    label: 'Clientes registrados',
    tone: 'yellow',
  },
  {
    detail: 'Fichas de pacientes',
    iconName: 'paw',
    key: 'pets',
    label: 'Mascotas registradas',
    tone: 'peach',
  },
]

function CardContent({ card, stats }) {
  return (
    <>
      <span className={`admin-summary-card__icon admin-summary-card__icon--${card.tone}`}>
        {card.icon
          ? <img src={card.icon} alt="" />
          : <Icon name={card.iconName} size={26} strokeWidth={2} />}
      </span>
      <span>
        <h2>{card.label}</h2>
        <strong>{stats[card.key]}</strong>
        <small>{card.detail}</small>
      </span>
    </>
  )
}

export default function SummaryGrid({ onOpenAppointments, stats }) {
  return (
    <section className="admin-summary-grid" aria-label="Resumen del negocio">
      {cards.map((card) => card.key === 'todayAppointments' ? (
        <button
          type="button"
          className="admin-summary-card"
          key={card.key}
          onClick={onOpenAppointments}
        >
          <CardContent card={card} stats={stats} />
        </button>
      ) : (
        <article className="admin-summary-card" key={card.key}>
          <CardContent card={card} stats={stats} />
        </article>
      ))}
    </section>
  )
}

import profileImage from '../../../assets/admin/profile.webp'
import {
  clinicCalendarDate,
  formatScheduleTime,
} from '../../utils/date'
import useManagementResource from '../../hooks/useManagementResource'
import AvailabilityManager from '../availability/AvailabilityManager'
import ClientManager from '../clients/ClientManager'
import NotificationManager from '../notifications/NotificationManager'
import PetManager from '../pets/PetManager'
import ReservationManager from '../reservations/ReservationManager'
import ServiceManager from '../services/ServiceManager'
import Icon from '../ui/Icon'
import Modal from '../ui/Modal'

function clinicDateKey(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function AgendaForDate({ api, date, onOpenAppointment }) {
  const dateKey = clinicDateKey(date)
  const agenda = useManagementResource(
    () => api.reservations(
      `?from=${dateKey}&to=${dateKey}&page=0&size=100`,
    ),
    [api, dateKey],
  )
  const schedule = agenda.data?.content ?? []

  return (
    <div className="admin-agenda-modal">
      <div className="admin-agenda-modal__date">
        <Icon name="calendar" size={22} />
        <span>
          <strong>
            {date.toLocaleDateString('es-CL', {
              weekday: 'long',
              day: 'numeric',
              month: 'long',
            })}
          </strong>
          <small>{schedule.length} atenciones programadas</small>
        </span>
      </div>
      {agenda.loading && !agenda.data && (
        <p className="admin-management-state" aria-busy="true">Cargando agenda…</p>
      )}
      {agenda.error && !agenda.data && (
        <div className="admin-management-error" role="alert">
          <p>{agenda.error.message}</p>
          <button type="button" onClick={agenda.reload}>Reintentar agenda</button>
        </div>
      )}
      {agenda.data && (
        <div className="admin-agenda-modal__list">
          {schedule.map((item) => (
            <button
              type="button"
              key={item.id}
              onClick={() => onOpenAppointment(item.id)}
            >
              <time>{formatScheduleTime(item.startsAt)}</time>
              <i />
              <span>
                <strong>{[...new Set(item.items.map((entry) => entry.serviceName))].join(', ')}</strong>
                <small>{item.items.map((entry) => entry.petName).join(', ')}</small>
              </span>
              <Icon name="chevronRight" size={16} />
            </button>
          ))}
          {!schedule.length && <p>No hay atenciones programadas para esta fecha.</p>}
        </div>
      )}
    </div>
  )
}

export default function DashboardModals({
  api,
  availabilityApi,
  data,
  modal,
  notificationResource,
  now,
  onClose,
  onOpenAppointment,
  onReloadDashboard,
}) {
  if (!modal) return null

  const modalTitles = {
    agenda: 'Calendario y agenda',
    appointment: 'Detalle de la reserva',
    appointments: 'Reservas',
    availability: 'Disponibilidad',
    clients: 'Clientes',
    notifications: 'Notificaciones operacionales',
    pets: 'Mascotas',
    profile: 'Mi perfil',
    report: `Reporte · ${modal.dataset?.label ?? ''}`,
    services: 'Servicios',
  }
  let content = null

  if (modal.type === 'appointment') {
    content = (
      <ReservationManager
        api={api}
        availabilityApi={availabilityApi}
        initialId={modal.id}
        now={now}
        onDashboardReload={onReloadDashboard}
      />
    )
  }

  if (modal.type === 'appointments') {
    content = (
      <ReservationManager
        api={api}
        availabilityApi={availabilityApi}
        now={now}
        onDashboardReload={onReloadDashboard}
        onOpen={onOpenAppointment}
      />
    )
  }

  if (modal.type === 'services') {
    content = <ServiceManager api={api} />
  }

  if (modal.type === 'availability') {
    content = <AvailabilityManager api={api} />
  }

  if (modal.type === 'clients') {
    content = <ClientManager api={api} onDashboardReload={onReloadDashboard} />
  }

  if (modal.type === 'pets') {
    content = <PetManager api={api} onDashboardReload={onReloadDashboard} />
  }

  if (modal.type === 'notifications') {
    content = (
      <NotificationManager api={api} notifications={notificationResource} />
    )
  }

  if (modal.type === 'report' && modal.dataset) {
    const total = modal.dataset.values.reduce((sum, value) => sum + value, 0)
    const bestValue = modal.dataset.values.length
      ? Math.max(...modal.dataset.values)
      : 0
    const bestIndex = modal.dataset.values.indexOf(bestValue)
    const average = modal.dataset.values.length
      ? Math.round(total / modal.dataset.values.length)
      : 0
    content = (
      <div className="admin-report">
        <div><strong>{total}</strong><span>reservas en el período</span></div>
        <div><strong>{average}</strong><span>promedio por intervalo</span></div>
        <div>
          <strong>{modal.dataset.labels[bestIndex] ?? 'Sin datos'}</strong>
          <span>intervalo con más reservas</span>
        </div>
      </div>
    )
  }

  if (modal.type === 'agenda') {
    const agendaDate = modal.date ?? clinicCalendarDate(now)
    content = (
      <AgendaForDate
        api={api}
        date={agendaDate}
        onOpenAppointment={onOpenAppointment}
      />
    )
  }

  if (modal.type === 'profile') {
    content = (
      <div className="admin-profile-detail">
        <img src={profileImage} alt="" />
        <h3>{data.profile.name}</h3>
        <p>Administradora · {data.profile.email}</p>
      </div>
    )
  }

  if (!modalTitles[modal.type]) return null
  const wide = [
    'appointment',
    'appointments',
    'availability',
    'clients',
    'notifications',
    'pets',
    'services',
  ].includes(modal.type)

  return (
    <Modal title={modalTitles[modal.type]} onClose={onClose} wide={wide}>
      {content}
    </Modal>
  )
}

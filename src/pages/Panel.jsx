import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { useNavigate } from 'react-router'
import { clientApi } from '../api/clientApi'
import { bookingApi } from '../api/bookingApi'
import { ApiError } from '../api/http'
import Appointments from '../components/panel/Appointments'
import AppointmentCard from '../components/panel/AppointmentCard'
import Hero from '../components/panel/Hero'
import Modal from '../components/panel/Modal'
import PanelFooter from '../components/panel/PanelFooter'
import PanelHeader from '../components/panel/PanelHeader'
import PetCard from '../components/panel/PetCard'
import PetForm from '../components/panel/PetForm'
import Pets from '../components/panel/Pets'
import Summary from '../components/panel/Summary'
import Toast from '../components/panel/Toast'
import {
  clientErrorFeedback,
  useClientPanel,
} from '../hooks/useClientPanel'
import { formatClinicDateTime } from '../utils/clinicTime'

const ACTIVE_RESERVATION_STATUSES = new Set(['PENDING', 'CONFIRMED'])
const MAX_TIMER_DELAY = 2_147_483_647

function ErrorFeedback({ feedback }) {
  if (!feedback) return null
  return (
    <div className="form-feedback" role="alert">
      <p>{feedback.message}</p>
      {feedback.fields.length > 0 && (
        <ul>
          {feedback.fields.map((field) => <li key={field}>{field}</li>)}
        </ul>
      )}
    </div>
  )
}

function ProfileForm({
  profile,
  pending,
  feedback,
  onCancel,
  onSubmit,
}) {
  const [name, setName] = useState(profile.name)
  const [phone, setPhone] = useState(profile.phone)
  const [validationError, setValidationError] = useState('')

  const submit = (event) => {
    event.preventDefault()
    const normalizedName = name.trim()
    const normalizedPhone = phone.trim()
    if (!normalizedName) {
      setValidationError('Ingresa tu nombre.')
      return
    }
    if (normalizedName.length > 120) {
      setValidationError('El nombre puede tener hasta 120 caracteres.')
      return
    }
    if (!normalizedPhone) {
      setValidationError('Ingresa tu teléfono.')
      return
    }
    if (normalizedPhone.length > 30) {
      setValidationError('El teléfono puede tener hasta 30 caracteres.')
      return
    }
    setValidationError('')
    onSubmit({ name: normalizedName, phone: normalizedPhone })
  }

  return (
    <form className="dialog-form" noValidate onSubmit={submit}>
      {validationError && <p className="form-feedback" role="alert">{validationError}</p>}
      {!validationError && <ErrorFeedback feedback={feedback} />}
      <label>
        Nombre
        <input
          name="name"
          maxLength="120"
          value={name}
          onChange={(event) => setName(event.target.value)}
          required
        />
      </label>
      <label>
        Teléfono
        <input
          name="phone"
          maxLength="30"
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          required
        />
      </label>
      <label>
        Correo electrónico
        <input value={profile.email} readOnly />
      </label>
      <div className="form-actions">
        <button
          className="button button--ghost"
          type="button"
          disabled={pending}
          onClick={onCancel}
        >
          Cancelar
        </button>
        <button className="button" type="submit" disabled={pending}>
          {pending ? 'Guardando…' : 'Guardar perfil'}
        </button>
      </div>
    </form>
  )
}

function CancelReservationForm({
  reservation,
  pending,
  feedback,
  onCancel,
  onSubmit,
}) {
  const [reason, setReason] = useState('')
  const [validationError, setValidationError] = useState('')
  const { date, time } = formatClinicDateTime(reservation.startsAt)
  const petNames = reservation.items.map((item) => item.petName).join(' y ')

  const submit = (event) => {
    event.preventDefault()
    const normalizedReason = reason.trim()
    if (Array.from(normalizedReason).length > 300) {
      setValidationError('El motivo puede tener hasta 300 caracteres.')
      return
    }
    setValidationError('')
    onSubmit(normalizedReason || null)
  }

  return (
    <form className="dialog-form confirmation" onSubmit={submit}>
      <p>
        Cancelarás la reserva de <strong>{petNames}</strong> para el {date} a
        las {time} hrs. El historial permanecerá disponible.
      </p>
      {validationError && <p className="form-feedback" role="alert">{validationError}</p>}
      {!validationError && <ErrorFeedback feedback={feedback} />}
      <label>
        Motivo (opcional)
        <textarea
          name="reason"
          maxLength="300"
          rows="4"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </label>
      <div className="form-actions">
        <button
          className="button button--ghost"
          type="button"
          disabled={pending}
          onClick={onCancel}
        >
          Volver
        </button>
        <button className="button button--secondary" type="submit" disabled={pending}>
          {pending ? 'Cancelando…' : 'Confirmar cancelación'}
        </button>
      </div>
    </form>
  )
}

function RescheduleReservationForm({
  availabilityApi,
  feedback,
  onCancel,
  onSuccess,
  onSubmit,
  pending,
  reservation,
}) {
  const initialDate = reservation.startsAt.slice(0, 10)
  const [from, setFrom] = useState(initialDate)
  const [to, setTo] = useState(initialDate)
  const [slots, setSlots] = useState([])
  const [selectedSlot, setSelectedSlot] = useState('')
  const [loadingSlots, setLoadingSlots] = useState(false)
  const [availabilityFeedback, setAvailabilityFeedback] = useState(null)
  const [mutationFeedback, setMutationFeedback] = useState(null)

  const loadSlots = async ({ preserveMutation = false } = {}) => {
    setAvailabilityFeedback(null)
    if (!preserveMutation) setMutationFeedback(null)
    setLoadingSlots(true)
    try {
      const result = await availabilityApi.availability(from, to)
      setSlots(result.filter((slot) => slot.startsAt !== reservation.startsAt))
      setSelectedSlot('')
      return true
    } catch (error) {
      setAvailabilityFeedback(clientErrorFeedback(
        error,
        'No pudimos cargar los horarios. Intenta nuevamente.',
      ))
      return false
    } finally {
      setLoadingSlots(false)
    }
  }

  const submit = async () => {
    if (!selectedSlot || pending) return
    setMutationFeedback(null)
    try {
      const result = await onSubmit(selectedSlot)
      if (result !== null) onSuccess(result)
    } catch (error) {
      const collision = error instanceof ApiError
        && error.status === 409
        && ['SLOT_ALREADY_BOOKED', 'SLOT_UNAVAILABLE'].includes(error.code)
      setMutationFeedback(collision
        ? {
            fields: [],
            message: 'Ese horario ya no estÃ¡ disponible. Elige otro horario disponible.',
          }
        : clientErrorFeedback(
            error,
            'No pudimos reprogramar la reserva. Intenta nuevamente.',
          ))
      if (collision) {
        await loadSlots({ preserveMutation: true })
      }
    }
  }

  const currentFeedback = availabilityFeedback ?? mutationFeedback ?? feedback

  return (
    <div className="dialog-form">
      <ErrorFeedback feedback={currentFeedback} />
      <div className="form-grid">
        <label>
          Desde
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label>
          Hasta
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
      </div>
      <button
        className="button button--ghost"
        type="button"
        disabled={!from || !to || loadingSlots || pending}
        onClick={() => loadSlots()}
      >
        {loadingSlots ? 'Buscandoâ€¦' : 'Buscar horarios'}
      </button>
      {availabilityFeedback && (
        <button
          className="button button--ghost"
          type="button"
          disabled={loadingSlots || pending}
          onClick={() => loadSlots()}
        >
          Reintentar horarios
        </button>
      )}
      <fieldset className="client-reschedule-slots">
        <legend>Horarios devueltos por el servidor</legend>
        {slots.map((slot) => (
          <label key={slot.startsAt}>
            <input
              type="radio"
              name="client-reschedule-slot"
              checked={selectedSlot === slot.startsAt}
              value={slot.startsAt}
              onChange={(event) => setSelectedSlot(event.target.value)}
            />
            {new Intl.DateTimeFormat('es-CL', {
              dateStyle: 'medium',
              timeStyle: 'short',
              timeZone: 'America/Santiago',
            }).format(new Date(slot.startsAt))}
          </label>
        ))}
        {!loadingSlots && !slots.length && <p>No hay horarios cargados.</p>}
      </fieldset>
      <div className="form-actions">
        <button
          className="button button--ghost"
          type="button"
          disabled={pending}
          onClick={onCancel}
        >
          Volver
        </button>
        <button
          className="button"
          type="button"
          disabled={!selectedSlot || pending}
          onClick={submit}
        >
          {pending ? 'Reprogramandoâ€¦' : 'Guardar reprogramaciÃ³n'}
        </button>
      </div>
    </div>
  )
}

function NotificationCenter({
  error,
  isPending,
  notifications,
  onRead,
  onReadAll,
  open,
  onToggle,
}) {
  const unreadCount = notifications.filter((notification) => notification.unread).length

  return (
    <section className="notification-center" aria-label="Notificaciones">
      <button
        className="notification-center__toggle"
        type="button"
        aria-expanded={open}
        aria-controls="client-notifications"
        aria-label={`Notificaciones (${unreadCount} sin leer)`}
        onClick={onToggle}
      >
        <span>Notificaciones</span>
        <strong>{unreadCount}</strong>
      </button>
      {open && (
        <div id="client-notifications" className="notification-center__list">
          <div className="notification-center__header">
            <h2>Notificaciones</h2>
            {notifications.length > 0 && (
              <button
                type="button"
                disabled={isPending('readAllNotifications')}
                onClick={onReadAll}
              >
                {isPending('readAllNotifications')
                  ? 'Marcando…'
                  : 'Marcar todas como leídas'}
              </button>
            )}
          </div>
          <ErrorFeedback feedback={error} />
          {notifications.length === 0 ? (
            <p className="notification-center__empty">No tienes notificaciones.</p>
          ) : notifications.map((notification) => {
            const { date, time } = formatClinicDateTime(notification.createdAt)
            const readKey = `readNotification:${notification.id}`
            return (
              <article
                key={notification.id}
                className={`notification ${notification.unread ? 'notification--unread' : ''}`}
              >
                <div>
                  <h3>{notification.title}</h3>
                  <p>{notification.body}</p>
                  <p className="notification__meta">
                    <time dateTime={notification.createdAt}>{date} · {time}</time>
                    {notification.reservationId
                      ? <> · <span>Reserva #{notification.reservationId}</span></>
                      : null}
                  </p>
                </div>
                {notification.unread && (
                  <button
                    type="button"
                    aria-label={`Marcar como leída: ${notification.title}`}
                    disabled={isPending(readKey)}
                    onClick={() => onRead(notification.id)}
                  >
                    {isPending(readKey) ? 'Marcando…' : 'Marcar como leída'}
                  </button>
                )}
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}

export default function Panel({
  api = clientApi,
  availabilityApi = bookingApi,
}) {
  const navigate = useNavigate()
  const panel = useClientPanel(api)
  const [dialog, setDialog] = useState(null)
  const [dialogError, setDialogError] = useState(null)
  const [notificationError, setNotificationError] = useState(null)
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [toast, setToast] = useState('')
  const [referenceTime, setReferenceTime] = useState(() => Date.now())
  const observedReservationsRef = useRef(panel.reservations)

  const closeDialog = useCallback(() => {
    setDialog(null)
    setDialogError(null)
  }, [])

  const openDialog = useCallback((nextDialog) => {
    setDialogError(null)
    setDialog(nextDialog)
  }, [])

  useEffect(() => {
    if (!toast) return undefined
    const timer = window.setTimeout(() => setToast(''), 2600)
    return () => window.clearTimeout(timer)
  }, [toast])

  useEffect(() => {
    let clockSyncActive = true
    if (observedReservationsRef.current !== panel.reservations) {
      observedReservationsRef.current = panel.reservations
      queueMicrotask(() => {
        if (clockSyncActive) {
          setReferenceTime((current) => Math.max(current, Date.now()))
        }
      })
    }

    const now = Date.now()
    const nextBoundary = panel.reservations
      .filter((reservation) => ACTIVE_RESERVATION_STATUSES.has(reservation.status))
      .map((reservation) => new Date(reservation.startsAt).getTime())
      .filter((startsAt) => Number.isFinite(startsAt) && startsAt > now)
      .sort((first, second) => first - second)[0]

    if (nextBoundary === undefined) {
      return () => {
        clockSyncActive = false
      }
    }

    const timer = window.setTimeout(
      () => setReferenceTime((current) => Math.max(current, Date.now())),
      Math.min(nextBoundary - now, MAX_TIMER_DELAY),
    )
    return () => {
      clockSyncActive = false
      window.clearTimeout(timer)
    }
  }, [panel.reservations, referenceTime])

  const nextReservation = useMemo(() => panel.reservations
    .filter((reservation) => ACTIVE_RESERVATION_STATUSES.has(reservation.status)
      && new Date(reservation.startsAt).getTime() > referenceTime)
    .sort((first, second) => (
      new Date(first.startsAt).getTime() - new Date(second.startsAt).getTime()
    ))[0] ?? null, [panel.reservations, referenceTime])

  const canCancel = useCallback((reservation) => (
    ACTIVE_RESERVATION_STATUSES.has(reservation.status)
    && new Date(reservation.startsAt).getTime() > referenceTime
  ), [referenceTime])

  const refreshWarningEntries = Object.entries(panel.refreshFailures)
    .filter(([, error]) => error)

  const saveProfile = async (body) => {
    setDialogError(null)
    try {
      const result = await panel.updateProfile(body)
      if (result === null) return
      closeDialog()
      setToast('Tu perfil fue actualizado.')
    } catch (error) {
      setDialogError(clientErrorFeedback(
        error,
        'No pudimos actualizar tu perfil. Intenta nuevamente.',
      ))
    }
  }

  const createPet = async (body) => {
    setDialogError(null)
    try {
      const result = await panel.createPet(body)
      if (result === null) return
      closeDialog()
      setToast('La mascota fue registrada correctamente.')
    } catch (error) {
      setDialogError(clientErrorFeedback(
        error,
        'No pudimos registrar la mascota. Intenta nuevamente.',
      ))
    }
  }

  const updatePet = async (petId, body) => {
    setDialogError(null)
    try {
      const result = await panel.updatePet(petId, body)
      if (result === null) return
      closeDialog()
      setToast('Los datos de la mascota fueron actualizados.')
    } catch (error) {
      setDialogError(clientErrorFeedback(
        error,
        'No pudimos actualizar la mascota. Intenta nuevamente.',
      ))
    }
  }

  const archivePet = async (petId) => {
    setDialogError(null)
    try {
      const result = await panel.archivePet(petId)
      if (result === null) return
      closeDialog()
      setToast('La mascota fue archivada. Su historial se conserva.')
    } catch (error) {
      setDialogError(clientErrorFeedback(
        error,
        'No pudimos archivar la mascota. Intenta nuevamente.',
      ))
    }
  }

  const cancelReservation = async (reservationId, reason) => {
    setDialogError(null)
    try {
      const result = await panel.cancelReservation(reservationId, reason)
      if (result === null) return
      closeDialog()
      setToast('La reserva fue cancelada y permanece en tu historial.')
    } catch (error) {
      setDialogError(clientErrorFeedback(
        error,
        'No pudimos cancelar la reserva. Intenta nuevamente.',
      ))
    }
  }

  const completeReschedule = () => {
    closeDialog()
    setToast('La reserva fue reprogramada con el estado informado por la clÃ­nica.')
  }

  const readNotification = async (notificationId) => {
    setNotificationError(null)
    try {
      const result = await panel.readNotification(notificationId)
      if (result === null) return
      setToast('Notificación marcada como leída.')
    } catch (error) {
      setNotificationError(clientErrorFeedback(
        error,
        'No pudimos marcar la notificación. Intenta nuevamente.',
      ))
    }
  }

  const readAllNotifications = async () => {
    setNotificationError(null)
    try {
      const result = await panel.readAllNotifications()
      if (result === null) return
      setToast(result.markedRead === 0
        ? 'No había notificaciones sin leer.'
        : 'Todas las notificaciones fueron marcadas como leídas.')
    } catch (error) {
      setNotificationError(clientErrorFeedback(
        error,
        'No pudimos marcar las notificaciones. Intenta nuevamente.',
      ))
    }
  }

  const modalContent = () => {
    if (!dialog) return null

    if (dialog.type === 'profile' && panel.profile) {
      return (
        <Modal title="Mi perfil" onClose={closeDialog}>
          <ProfileForm
            profile={panel.profile}
            pending={panel.isPending('updateProfile')}
            feedback={dialogError}
            onCancel={closeDialog}
            onSubmit={saveProfile}
          />
        </Modal>
      )
    }

    if (dialog.type === 'create-pet') {
      return (
        <Modal title="Registrar nueva mascota" onClose={closeDialog}>
          <ErrorFeedback feedback={dialogError} />
          <PetForm
            pending={panel.isPending('createPet')}
            onCancel={closeDialog}
            onSubmit={createPet}
          />
        </Modal>
      )
    }

    if (dialog.type === 'all-reservations') {
      return (
        <Modal title="Todas mis reservas" onClose={closeDialog} wide>
          <div className="modal-list">
            {panel.reservations.length ? panel.reservations.map((reservation) => (
              <AppointmentCard
                key={reservation.id}
                reservation={reservation}
                onCancel={(selected) => openDialog({
                  reservation: selected,
                  type: 'cancel-reservation',
                })}
                onReschedule={(selected) => openDialog({
                  reservation: selected,
                  type: 'reschedule-reservation',
                })}
                canCancel={canCancel(reservation)}
                canReschedule={canCancel(reservation)}
              />
            )) : <p className="modal-empty">No tienes reservas registradas.</p>}
          </div>
        </Modal>
      )
    }

    if (dialog.type === 'all-pets') {
      return (
        <Modal title="Mis mascotas registradas" onClose={closeDialog}>
          <div className="modal-list">
            {panel.pets.length ? panel.pets.map((pet) => (
              <PetCard
                key={pet.id}
                pet={pet}
                onView={(selectedPet) => openDialog({
                  petId: selectedPet.id,
                  type: 'pet-detail',
                })}
              />
            )) : <p className="modal-empty">No tienes mascotas activas registradas.</p>}
          </div>
        </Modal>
      )
    }

    const selectedPet = dialog.petId
      ? panel.pets.find((pet) => pet.id === dialog.petId)
      : null

    if (dialog.type === 'pet-detail' && selectedPet) {
      return (
        <Modal title={`Datos de ${selectedPet.name}`} onClose={closeDialog}>
          <div className="pet-detail">
            <PetCard pet={selectedPet} compact />
            <dl>
              <div><dt>Nombre</dt><dd>{selectedPet.name}</dd></div>
              <div><dt>Especie</dt><dd>{selectedPet.species}</dd></div>
              <div><dt>Raza</dt><dd>{selectedPet.breed || 'No informada'}</dd></div>
              <div><dt>Nacimiento</dt><dd>{selectedPet.birthdate || 'No informado'}</dd></div>
            </dl>
            <div className="pet-detail__actions">
              <button
                className="button button--ghost"
                type="button"
                onClick={() => openDialog({
                  petId: selectedPet.id,
                  type: 'edit-pet',
                })}
              >
                Editar mascota
              </button>
              <button
                className="button button--secondary"
                type="button"
                onClick={() => openDialog({
                  petId: selectedPet.id,
                  type: 'archive-pet',
                })}
              >
                Archivar mascota
              </button>
            </div>
          </div>
        </Modal>
      )
    }

    if (dialog.type === 'edit-pet' && selectedPet) {
      return (
        <Modal title={`Editar a ${selectedPet.name}`} onClose={closeDialog}>
          <ErrorFeedback feedback={dialogError} />
          <PetForm
            initialPet={selectedPet}
            pending={panel.isPending(`updatePet:${selectedPet.id}`)}
            onCancel={closeDialog}
            onSubmit={(body) => updatePet(selectedPet.id, body)}
          />
        </Modal>
      )
    }

    if (dialog.type === 'archive-pet' && selectedPet) {
      const pending = panel.isPending(`archivePet:${selectedPet.id}`)
      return (
        <Modal title="Archivar mascota" onClose={closeDialog}>
          <div className="confirmation">
            <p>
              <strong>{selectedPet.name}</strong> dejará de aparecer entre tus
              mascotas activas. Su historial de reservas permanecerá disponible.
            </p>
            <ErrorFeedback feedback={dialogError} />
            <div className="form-actions">
              <button
                className="button button--ghost"
                type="button"
                disabled={pending}
                onClick={closeDialog}
              >
                Volver
              </button>
              <button
                className="button button--secondary"
                type="button"
                disabled={pending}
                onClick={() => archivePet(selectedPet.id)}
              >
                {pending ? 'Archivando…' : 'Sí, archivar'}
              </button>
            </div>
          </div>
        </Modal>
      )
    }

    if (dialog.type === 'cancel-reservation') {
      const pending = panel.isPending(
        `cancelReservation:${dialog.reservation.id}`,
      )
      return (
        <Modal title="Cancelar reserva" onClose={closeDialog}>
          <CancelReservationForm
            reservation={dialog.reservation}
            pending={pending}
            feedback={dialogError}
            onCancel={closeDialog}
            onSubmit={(reason) => cancelReservation(
              dialog.reservation.id,
              reason,
            )}
          />
        </Modal>
      )
    }

    if (dialog.type === 'reschedule-reservation') {
      const pending = panel.isPending(
        `rescheduleReservation:${dialog.reservation.id}`,
      )
      return (
        <Modal title="Reprogramar reserva" onClose={closeDialog}>
          <RescheduleReservationForm
            availabilityApi={availabilityApi}
            feedback={dialogError}
            pending={pending}
            reservation={dialog.reservation}
            onCancel={closeDialog}
            onSubmit={(startsAt) => panel.rescheduleReservation(
              dialog.reservation.id,
              startsAt,
            )}
            onSuccess={completeReschedule}
          />
        </Modal>
      )
    }

    return null
  }

  return (
    <div className="panel-page">
      <div className="page-shell">
        <div className="app-frame">
          <PanelHeader
            profileName={panel.profile?.name}
            onProfileAction={() => openDialog({ type: 'profile' })}
          />
          <main className="dashboard">
            <div className="dashboard__surface">
              <Hero />
              {panel.loading ? (
                <p className="panel-load-state">Cargando tu panel…</p>
              ) : panel.loadError ? (
                <div className="panel-load-state panel-load-state--error">
                  <p role="alert">{panel.loadError}</p>
                  <button
                    className="button"
                    type="button"
                    onClick={panel.retry}
                  >
                    Reintentar
                  </button>
                </div>
              ) : (
                <>
                  <NotificationCenter
                    error={notificationError}
                    isPending={panel.isPending}
                    notifications={panel.notifications}
                    onRead={readNotification}
                    onReadAll={readAllNotifications}
                    open={notificationsOpen}
                    onToggle={() => {
                      setReferenceTime((current) => Math.max(current, Date.now()))
                      setNotificationsOpen((current) => !current)
                    }}
                  />
                  {refreshWarningEntries.map(([resource, error]) => (
                    <div className="panel-refresh-warning" role="alert" key={resource}>
                      <p>
                        El cambio se guardÃ³, pero no pudimos actualizar
                        {' '}{resource === 'pets' ? 'tus mascotas' : 'tus reservas'}.
                        {' '}{error.message}
                      </p>
                      <button
                        className="button button--ghost"
                        type="button"
                        onClick={() => panel.retryRefresh(resource)}
                      >
                        Reintentar actualizaciÃ³n
                      </button>
                    </div>
                  ))}
                  <Summary
                    appointmentCount={panel.reservations.length}
                    petCount={panel.pets.length}
                    nextAppointment={nextReservation}
                    onViewAppointments={() => openDialog({ type: 'all-reservations' })}
                    onViewPets={() => openDialog({ type: 'all-pets' })}
                  />
                  <div className="dashboard__columns">
                    <Appointments
                      reservations={panel.reservations}
                      onCancel={(reservation) => openDialog({
                        reservation,
                        type: 'cancel-reservation',
                      })}
                      onReschedule={(reservation) => openDialog({
                        reservation,
                        type: 'reschedule-reservation',
                      })}
                      canCancel={canCancel}
                      onViewAll={() => openDialog({ type: 'all-reservations' })}
                      onAdd={() => navigate('/reservar')}
                    />
                    <Pets
                      pets={panel.pets}
                      onView={(pet) => openDialog({
                        petId: pet.id,
                        type: 'pet-detail',
                      })}
                      onViewAll={() => openDialog({ type: 'all-pets' })}
                      onAdd={() => openDialog({ type: 'create-pet' })}
                    />
                  </div>
                </>
              )}
            </div>
          </main>
          <PanelFooter />
        </div>
      </div>
      {modalContent()}
      <Toast message={toast} />
    </div>
  )
}

import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../api/http'
import { formatScheduleTime } from '../../utils/date'
import { ErrorManagement } from '../management/ManagementState'

const ACTIONS = {
  PENDING: [
    ['Confirmar', 'CONFIRMED'],
    ['Cancelar', 'CANCELLED'],
  ],
  CONFIRMED: [
    ['Cancelar', 'CANCELLED'],
    ['Completar', 'COMPLETED'],
    ['Marcar como inasistencia', 'NO_SHOW'],
  ],
}

function correctiveMessage(error) {
  if (!(error instanceof ApiError) || error.status !== 409) return error?.message
  if (['SLOT_UNAVAILABLE', 'SLOT_ALREADY_BOOKED'].includes(error.code)) {
    return 'Ese horario ya no está disponible. Elige otro horario disponible.'
  }
  if (error.code === 'INVALID_RESERVATION_STATUS_TRANSITION') {
    return 'El estado cambió mientras trabajabas. Revisa el detalle actualizado.'
  }
  return error.message
}

export default function ReservationActions({
  availabilityApi,
  disabled,
  onChangeStatus,
  onReschedule,
  reservation,
}) {
  const [mode, setMode] = useState(null)
  const [reason, setReason] = useState('')
  const [from, setFrom] = useState(reservation.startsAt.slice(0, 10))
  const [to, setTo] = useState(reservation.startsAt.slice(0, 10))
  const [slots, setSlots] = useState([])
  const [selectedSlot, setSelectedSlot] = useState('')
  const [loadingSlots, setLoadingSlots] = useState(false)
  const [error, setError] = useState(null)
  const mountedRef = useRef(false)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  const runStatus = async (status, nextReason = '') => {
    setError(null)
    try {
      await onChangeStatus(status, nextReason)
      if (!mountedRef.current) return
      setMode(null)
      setReason('')
    } catch (nextError) {
      if (mountedRef.current) setError(nextError)
    }
  }

  const findSlots = async () => {
    setError(null)
    setLoadingSlots(true)
    try {
      const result = await availabilityApi.availability(from, to)
      if (!mountedRef.current) return
      setSlots(result.filter((slot) => slot.startsAt !== reservation.startsAt))
      setSelectedSlot('')
    } catch (nextError) {
      if (mountedRef.current) setError(nextError)
    } finally {
      if (mountedRef.current) setLoadingSlots(false)
    }
  }

  const submitReschedule = async () => {
    setError(null)
    try {
      await onReschedule(selectedSlot)
      if (!mountedRef.current) return
      setMode(null)
    } catch (nextError) {
      if (mountedRef.current) setError(nextError)
    }
  }

  if (mode === 'cancel') {
    return (
      <section className="admin-management-form" aria-label="Cancelar reserva">
        {error && <ErrorManagement error={{ message: correctiveMessage(error) }} />}
        <p>La reserva y su historial se conservarán. Esta acción no elimina registros.</p>
        <label>
          Motivo de cancelación (opcional)
          <textarea
            maxLength="300"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
        <div className="admin-modal-actions">
          <button type="button" className="is-secondary" onClick={() => setMode(null)}>
            Volver
          </button>
          <button
            type="button"
            disabled={disabled}
            onClick={() => runStatus('CANCELLED', reason.trim())}
          >
            Confirmar cancelación
          </button>
        </div>
      </section>
    )
  }

  if (mode === 'reschedule') {
    return (
      <section className="admin-management-form" aria-label="Reprogramar reserva">
        {error && <ErrorManagement error={{ message: correctiveMessage(error) }} />}
        <div className="admin-management-fields">
          <label>Desde<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
          <label>Hasta<input type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label>
        </div>
        <button type="button" disabled={!from || !to || loadingSlots} onClick={findSlots}>
          {loadingSlots ? 'Buscando…' : 'Buscar horarios'}
        </button>
        <fieldset className="admin-management-slots">
          <legend>Horarios devueltos por el servidor</legend>
          {slots.map((slot) => (
            <label key={slot.startsAt}>
              <input
                type="radio"
                name="reschedule-slot"
                value={slot.startsAt}
                checked={selectedSlot === slot.startsAt}
                onChange={(event) => setSelectedSlot(event.target.value)}
              />
              {new Intl.DateTimeFormat('es-CL', {
                dateStyle: 'medium',
                timeZone: 'America/Santiago',
              }).format(new Date(slot.startsAt))} · {formatScheduleTime(slot.startsAt)}
            </label>
          ))}
          {!loadingSlots && !slots.length && <p>No hay horarios cargados.</p>}
        </fieldset>
        <div className="admin-modal-actions">
          <button type="button" className="is-secondary" onClick={() => setMode(null)}>Volver</button>
          <button type="button" disabled={!selectedSlot || disabled} onClick={submitReschedule}>
            Guardar reprogramación
          </button>
        </div>
      </section>
    )
  }

  const actions = ACTIONS[reservation.status] ?? []
  const canReschedule = ['PENDING', 'CONFIRMED'].includes(reservation.status)

  return (
    <>
      {error && <ErrorManagement error={{ message: correctiveMessage(error) }} />}
      <div className="admin-modal-actions">
        {actions.map(([label, status]) => (
          <button
            type="button"
            key={status}
            disabled={disabled}
            onClick={() => status === 'CANCELLED'
              ? setMode('cancel')
              : runStatus(status, '')}
          >
            {label}
          </button>
        ))}
        {canReschedule && (
          <button type="button" disabled={disabled} onClick={() => setMode('reschedule')}>
            Reprogramar
          </button>
        )}
      </div>
    </>
  )
}

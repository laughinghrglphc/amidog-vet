import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError } from '../../../api/http'
import { statusLabel } from '../../../utils/statusLabels'
import useManagementResource from '../../hooks/useManagementResource'
import {
  formatAppointmentDate,
  formatClinicDateTime,
} from '../../utils/date'
import {
  EmptyManagement,
  ErrorManagement,
  LoadingManagement,
  StatusToast,
} from '../management/ManagementState'
import ReservationActions from './ReservationActions'

const EVENT_LABELS = {
  CANCELLED: 'Cancelada',
  CREATED: 'Creada',
  RESCHEDULED: 'Reprogramada',
  STATUS_CHANGED: 'Estado actualizado',
}

const actorLabel = (actor) => ({
  ADMIN: 'Administración',
  CLIENT: 'Cliente',
  SYSTEM: 'Sistema',
}[actor] ?? actor)

const optionalDateTime = (value) =>
  value ? formatClinicDateTime(value) : 'No aplica'

function queryString(filters, page) {
  const params = new URLSearchParams()
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.status) params.set('status', filters.status)
  if (filters.q.trim()) params.set('q', filters.q.trim())
  params.set('page', String(page))
  params.set('size', '25')
  return `?${params.toString()}`
}

function reloadFailure(result) {
  if (Array.isArray(result)) {
    return result.map(reloadFailure).find(Boolean) ?? null
  }
  if (result?.status === 'failure') return result.error
  if (['disposed', 'stale'].includes(result?.status)) {
    return new Error('La actualización quedó obsoleta.')
  }
  return null
}

function isTransitionConflict(error) {
  return error instanceof ApiError
    && error.status === 409
    && error.code === 'INVALID_RESERVATION_STATUS_TRANSITION'
}

export default function ReservationManager({
  api,
  availabilityApi,
  initialId = null,
  now,
  onDashboardReload,
  onOpen,
}) {
  const [filters, setFilters] = useState({ from: '', q: '', status: '', to: '' })
  const [appliedFilters, setAppliedFilters] = useState(filters)
  const [page, setPage] = useState(0)
  const [pending, setPending] = useState(false)
  const [toast, setToast] = useState('')
  const [filterError, setFilterError] = useState(null)
  const [actionRefreshError, setActionRefreshError] = useState(null)
  const mountedRef = useRef(false)
  const listQuery = useMemo(
    () => queryString(appliedFilters, page),
    [appliedFilters, page],
  )
  const list = useManagementResource(
    () => initialId ? Promise.resolve(null) : api.reservations(listQuery),
    [api, initialId, listQuery],
  )
  const detail = useManagementResource(
    () => initialId ? api.reservation(initialId) : Promise.resolve(null),
    [api, initialId],
  )

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  const reloadAffected = async () => {
    const results = await Promise.all([
      initialId ? api.reservations(listQuery) : list.reload(),
      initialId ? detail.reload() : Promise.resolve(null),
      onDashboardReload?.(),
    ])
    const refreshError = reloadFailure(results)
    if (refreshError) throw refreshError
    return results[1]?.value ?? results[1]
  }

  const changeStatus = async (status, reason) => {
    setPending(true)
    setActionRefreshError(null)
    try {
      const saved = await api.changeStatus(initialId, { reason, status })
      if (!mountedRef.current) return
      detail.setData((current) => current ? { ...current, ...saved } : current)
      try {
        await reloadAffected()
      } catch (refreshError) {
        if (!mountedRef.current) return
        setActionRefreshError(new Error(
          'El estado se guardó, pero no pudimos actualizar el detalle.',
          { cause: refreshError },
        ))
        return
      }
      if (!mountedRef.current) return
      setToast(status === 'CONFIRMED'
        ? 'Reserva confirmada.'
        : 'Reserva actualizada.')
    } catch (error) {
      if (!mountedRef.current) return
      if (isTransitionConflict(error)) {
        try {
          await reloadAffected()
        } catch (refreshError) {
          if (mountedRef.current) {
            setActionRefreshError(new Error(
              'El estado cambió mientras trabajabas y no pudimos actualizar el detalle.',
              { cause: refreshError },
            ))
          }
          return
        }
      }
      if (mountedRef.current) throw error
    } finally {
      if (mountedRef.current) setPending(false)
    }
  }

  const reschedule = async (startsAt) => {
    setPending(true)
    setActionRefreshError(null)
    try {
      const saved = await api.reschedule(initialId, startsAt)
      if (!mountedRef.current) return
      detail.setData((current) => current ? { ...current, ...saved } : current)
      try {
        await reloadAffected()
      } catch (refreshError) {
        if (!mountedRef.current) return
        setActionRefreshError(new Error(
          'La reprogramación se guardó, pero no pudimos actualizar el detalle.',
          { cause: refreshError },
        ))
        return
      }
      if (!mountedRef.current) return
      setToast('Reserva reprogramada.')
    } catch (error) {
      if (mountedRef.current) throw error
    } finally {
      if (mountedRef.current) setPending(false)
    }
  }

  const retryActionRefresh = async () => {
    setPending(true)
    try {
      await reloadAffected()
      if (mountedRef.current) setActionRefreshError(null)
    } catch {
      if (mountedRef.current) {
        setActionRefreshError(new Error(
          'El estado cambió mientras trabajabas y no pudimos actualizar el detalle.',
        ))
      }
    } finally {
      if (mountedRef.current) setPending(false)
    }
  }

  if (initialId) {
    if (detail.loading && !detail.data) return <LoadingManagement label="Cargando reserva" />
    if (detail.error && !detail.data) return <ErrorManagement error={detail.error} onRetry={detail.reload} />
    if (!detail.data) return <EmptyManagement>No encontramos la reserva.</EmptyManagement>
    const reservation = detail.data
    return (
      <div className="admin-management">
        <StatusToast>{toast}</StatusToast>
        <dl className="admin-management-detail">
          <div><dt>Mascotas y servicios</dt><dd>{reservation.items.map((item) => (
            <span key={`${item.petId}-${item.serviceId}`}>
              {item.petName} · {item.species}
              {item.breed ? ` · ${item.breed}` : ''}
              {' · '}{item.serviceName}
            </span>
          ))}</dd></div>
          <div><dt>Cliente</dt><dd>{reservation.clientName}</dd></div>
          <div><dt>Contacto</dt><dd>{reservation.clientEmail} · {reservation.clientPhone}</dd></div>
          <div>
            <dt>Inicio</dt>
            <dd>
              <span>{formatAppointmentDate(reservation.startsAt, now)}</span>
              <span>{formatClinicDateTime(reservation.startsAt)}</span>
            </dd>
          </div>
          <div><dt>Fin</dt><dd>{formatClinicDateTime(reservation.endsAt)}</dd></div>
          <div><dt>Creada</dt><dd>{formatClinicDateTime(reservation.createdAt)}</dd></div>
          <div><dt>Actualizada</dt><dd>{formatClinicDateTime(reservation.updatedAt)}</dd></div>
          <div><dt>Estado</dt><dd>{statusLabel(reservation.status)}</dd></div>
          {reservation.clientNote && <div><dt>Nota</dt><dd>{reservation.clientNote}</dd></div>}
          {reservation.cancelledBy && (
            <div><dt>Cancelada por</dt><dd>{actorLabel(reservation.cancelledBy)}</dd></div>
          )}
          {reservation.cancelledAt && (
            <div><dt>Cancelada el</dt><dd>{formatClinicDateTime(reservation.cancelledAt)}</dd></div>
          )}
          {reservation.cancellationReason && <div><dt>Motivo de cancelación</dt><dd>{reservation.cancellationReason}</dd></div>}
        </dl>
        <section className="admin-management-history">
          <h3>Historial</h3>
          {reservation.events.map((event) => (
            <article key={event.id}>
              <p>
                <strong>{EVENT_LABELS[event.eventType] ?? event.eventType}</strong>
                {' · '}{actorLabel(event.actor)}
              </p>
              <p>
                Estado anterior: {event.previousStatus
                  ? statusLabel(event.previousStatus)
                  : 'No aplica'}
                {' · '}Estado nuevo: {event.newStatus
                  ? statusLabel(event.newStatus)
                  : 'No aplica'}
              </p>
              <p>
                Horario anterior: {optionalDateTime(event.previousStartsAt)}
                {' · '}Horario nuevo: {optionalDateTime(event.newStartsAt)}
              </p>
              <p>Motivo: {event.reason || 'Sin motivo'}</p>
              <p>Fecha del evento: {formatClinicDateTime(event.createdAt)}</p>
            </article>
          ))}
          {!reservation.events.length && <p>Sin eventos registrados.</p>}
        </section>
        {actionRefreshError ? (
          <ErrorManagement
            error={actionRefreshError}
            onRetry={retryActionRefresh}
            retryLabel="Reintentar detalle actualizado"
          />
        ) : (
          <ReservationActions
            availabilityApi={availabilityApi}
            disabled={pending}
            onChangeStatus={changeStatus}
            onReschedule={reschedule}
            reservation={reservation}
          />
        )}
      </div>
    )
  }

  return (
    <div className="admin-management">
      <form
        className="admin-management-filters"
        onSubmit={(event) => {
          event.preventDefault()
          const dates = [filters.from, filters.to].filter(Boolean)
          if (dates.some((date) =>
            date < '1900-01-01' || date > '2100-12-31')) {
            setFilterError(new Error(
              'Las fechas deben estar entre 1900 y 2100.',
            ))
            return
          }
          if (filters.from && filters.to && filters.from > filters.to) {
            setFilterError(new Error(
              'La fecha desde debe ser anterior o igual a la fecha hasta.',
            ))
            return
          }
          setFilterError(null)
          setPage(0)
          setAppliedFilters(filters)
        }}
      >
        <label>Buscar reservas<input type="search" maxLength="120" value={filters.q} onChange={(event) => setFilters({ ...filters, q: event.target.value })} /></label>
        <label>Estado<select value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}>
          <option value="">Todos</option>
          {['PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'].map((value) => (
            <option key={value} value={value}>{statusLabel(value)}</option>
          ))}
        </select></label>
        <label>Desde<input type="date" value={filters.from} onChange={(event) => setFilters({ ...filters, from: event.target.value })} /></label>
        <label>Hasta<input type="date" value={filters.to} onChange={(event) => setFilters({ ...filters, to: event.target.value })} /></label>
        <button type="submit">Aplicar filtros</button>
      </form>
      {filterError && <ErrorManagement error={filterError} />}
      {list.loading && <LoadingManagement label="Cargando reservas" />}
      {list.error && <ErrorManagement error={list.error} onRetry={list.reload} />}
      {!list.loading && !list.error && list.data?.content.map((reservation) => (
        <button
          className="admin-management-row"
          type="button"
          key={reservation.id}
          onClick={() => onOpen(reservation.id)}
        >
          <strong>
            {reservation.items.map((item) =>
              `${item.petName} · ${item.species}${item.breed ? ` · ${item.breed}` : ''} · ${item.serviceName}`)
              .join(' · ')}
          </strong>
          <span>{reservation.clientName} · {reservation.clientEmail} · {reservation.clientPhone}</span>
          {reservation.clientNote && <span>Nota: {reservation.clientNote}</span>}
          <span>
            Inicio: {formatClinicDateTime(reservation.startsAt)}
            {' · '}Fin: {formatClinicDateTime(reservation.endsAt)}
          </span>
          <span>
            Creada: {formatClinicDateTime(reservation.createdAt)}
            {' · '}Actualizada: {formatClinicDateTime(reservation.updatedAt)}
          </span>
          <em>{statusLabel(reservation.status)}</em>
        </button>
      ))}
      {!list.loading && !list.error && !list.data?.content.length && (
        <EmptyManagement>No hay reservas para estos filtros.</EmptyManagement>
      )}
      {list.data && (
        <nav className="admin-management-pagination" aria-label="Paginación de reservas">
          <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Anterior</button>
          <span>Página {list.data.page + 1} de {Math.max(1, list.data.totalPages)}</span>
          <button type="button" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage((value) => value + 1)}>Siguiente</button>
        </nav>
      )}
    </div>
  )
}

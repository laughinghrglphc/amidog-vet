import { useState } from 'react'
import useManagementResource from '../../hooks/useManagementResource'
import useMutationLifecycle from '../../hooks/useMutationLifecycle'
import {
  clinicDateRange,
  clinicLocalDateTimeToOffset,
} from '../../utils/clinicLocalDateTime'
import { formatAppointmentDate } from '../../utils/date'
import {
  EmptyManagement,
  ErrorManagement,
  LoadingManagement,
  StatusToast,
} from '../management/ManagementState'

const DAYS = {
  1: 'Lunes',
  2: 'Martes',
  3: 'Miércoles',
  4: 'Jueves',
  5: 'Viernes',
  6: 'Sábado',
  7: 'Domingo',
}

function normalizeTime(value) {
  return value.length === 5 ? `${value}:00` : value
}

function WeeklyEditor({ error, initial, pending, onSave }) {
  const [intervals, setIntervals] = useState(
    initial.map(({ dayOfWeek, start, end, active }) => ({
      active,
      dayOfWeek,
      end,
      start,
    })),
  )
  const [newDay, setNewDay] = useState('1')
  const [validationError, setValidationError] = useState(null)

  const update = (index, patch) => {
    setIntervals((current) => current.map((interval, currentIndex) =>
      currentIndex === index ? { ...interval, ...patch } : interval))
  }

  const save = () => {
    const normalized = intervals.map((interval) => ({
      dayOfWeek: Number(interval.dayOfWeek),
      start: normalizeTime(interval.start),
      end: normalizeTime(interval.end),
      active: interval.active,
    }))
    if (normalized.some((interval) => interval.start >= interval.end)) {
      setValidationError(new Error(
        'El inicio de cada intervalo debe ser anterior al fin.',
      ))
      return
    }
    const activeByDay = normalized
      .filter((interval) => interval.active)
      .reduce((days, interval) => {
        const current = days.get(interval.dayOfWeek) ?? []
        current.push(interval)
        days.set(interval.dayOfWeek, current)
        return days
      }, new Map())
    const overlaps = [...activeByDay.values()].some((dayIntervals) => {
      const sorted = [...dayIntervals].sort((left, right) =>
        left.start.localeCompare(right.start))
      return sorted.some((interval, index) =>
        index > 0 && interval.start < sorted[index - 1].end)
    })
    if (overlaps) {
      setValidationError(new Error(
        'Los intervalos activos del mismo día no pueden superponerse.',
      ))
      return
    }
    setValidationError(null)
    void onSave(normalized)
  }

  return (
    <section className="admin-management-section">
      <h3>Horario semanal completo</h3>
      <p>Guardar reemplaza la lista completa, incluidos los intervalos inactivos.</p>
      {(validationError ?? error) && (
        <ErrorManagement error={validationError ?? error} />
      )}
      <div className="admin-weekly-list">
        {intervals.map((interval, index) => (
          <div className="admin-weekly-row" key={`${index}-${interval.dayOfWeek}`}>
            <span>{DAYS[interval.dayOfWeek]}</span>
            <label>
              <span className="sr-only">Inicio {DAYS[interval.dayOfWeek]} {index + 1}</span>
              <input type="time" value={interval.start.slice(0, 5)} onChange={(event) => update(index, { start: event.target.value })} />
            </label>
            <label>
              <span className="sr-only">Fin {DAYS[interval.dayOfWeek]} {index + 1}</span>
              <input type="time" value={interval.end.slice(0, 5)} onChange={(event) => update(index, { end: event.target.value })} />
            </label>
            <label><input type="checkbox" checked={interval.active} onChange={(event) => update(index, { active: event.target.checked })} /> Activo</label>
            <button type="button" aria-label={`Quitar intervalo ${index + 1}`} onClick={() => setIntervals((current) => current.filter((_, currentIndex) => currentIndex !== index))}>Quitar</button>
          </div>
        ))}
      </div>
      <label>
        Día del nuevo intervalo
        <select value={newDay} onChange={(event) => setNewDay(event.target.value)}>
          {Object.entries(DAYS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <button
        type="button"
        onClick={() => setIntervals((current) => [...current, {
          active: true,
          dayOfWeek: Number(newDay),
          end: '17:00:00',
          start: '09:00:00',
        }])}
      >
        Agregar intervalo
      </button>
      <button type="button" disabled={pending} onClick={save}>Guardar horario semanal</button>
    </section>
  )
}

function BlockEditor({
  blocks,
  error,
  pending,
  onCreate,
  onDelete,
}) {
  const [fullDay, setFullDay] = useState(false)
  const [date, setDate] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [reason, setReason] = useState('')
  const [inputError, setInputError] = useState(null)

  const submit = (event) => {
    event.preventDefault()
    try {
      const range = fullDay
        ? clinicDateRange(date)
        : {
            startsAt: clinicLocalDateTimeToOffset(startsAt),
            endsAt: clinicLocalDateTimeToOffset(endsAt),
          }
      if (new Date(range.endsAt) <= new Date(range.startsAt)) {
        throw new Error('El fin debe ser posterior al inicio.')
      }
      setInputError(null)
      void onCreate({ ...range, reason: reason.trim() || null }).then((completed) => {
        if (!completed) return
        setDate('')
        setStartsAt('')
        setEndsAt('')
        setReason('')
      })
    } catch (nextError) {
      setInputError(nextError)
    }
  }

  const displayedError = inputError ?? error
  const reservationIds = displayedError?.code === 'BLOCK_OVERLAPS_RESERVATIONS'
    && Array.isArray(displayedError.details?.reservationIds)
    ? displayedError.details.reservationIds.filter(Number.isInteger)
    : []

  return (
    <section className="admin-management-section">
      <h3>Bloqueos excepcionales</h3>
      {displayedError && (
        <div className="admin-management-error" role="alert">
          <p>{displayedError.message}</p>
          {reservationIds.length > 0 && (
            <p>Reservas afectadas: {reservationIds.join(', ')}. Ajusta el rango antes de reintentar.</p>
          )}
        </div>
      )}
      <form className="admin-management-form" onSubmit={submit}>
        <label><input type="checkbox" checked={fullDay} onChange={(event) => setFullDay(event.target.checked)} /> Día completo</label>
        {fullDay ? (
          <label>Fecha del bloqueo<input required type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>
        ) : (
          <div className="admin-management-fields">
            <label>Inicio del bloqueo<input required type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></label>
            <label>
              Fin del bloqueo
              <input
                required
                type="datetime-local"
                value={endsAt}
                aria-invalid={Boolean(inputError)}
                onChange={(event) => {
                  setEndsAt(event.target.value)
                  setInputError(null)
                }}
              />
            </label>
          </div>
        )}
        <label>Motivo (opcional)<input maxLength="200" value={reason} onChange={(event) => setReason(event.target.value)} /></label>
        <button type="submit" disabled={pending}>Crear bloqueo</button>
      </form>
      <div className="admin-management-list">
        {blocks.map((block) => (
          <article className="admin-management-item" key={block.id}>
            <div>
              <strong>{block.reason || 'Sin motivo'}</strong>
              <small>{formatAppointmentDate(block.startsAt)} — {formatAppointmentDate(block.endsAt)}</small>
            </div>
            <button type="button" disabled={pending} aria-label={`Eliminar bloqueo ${block.id}`} onClick={() => onDelete(block.id)}>Eliminar bloqueo</button>
          </article>
        ))}
        {!blocks.length && <EmptyManagement>No hay bloqueos configurados.</EmptyManagement>}
      </div>
    </section>
  )
}

export default function AvailabilityManager({ api }) {
  const weekly = useManagementResource(() => api.weeklyAvailability(), [api])
  const blocks = useManagementResource(() => api.blocks(), [api])
  const [weeklyToast, setWeeklyToast] = useState('')
  const [blockToast, setBlockToast] = useState('')
  const [weeklyError, setWeeklyError] = useState(null)
  const [blockError, setBlockError] = useState(null)
  const [weeklyRefreshError, setWeeklyRefreshError] = useState(null)
  const [blockRefreshError, setBlockRefreshError] = useState(null)
  const mutations = useMutationLifecycle()

  const retryWeeklyRefresh = async () => {
    const result = await weekly.reload()
    if (result.status === 'success') setWeeklyRefreshError(null)
  }

  const retryBlockRefresh = async () => {
    const result = await blocks.reload()
    if (result.status === 'success') setBlockRefreshError(null)
  }

  const saveWeekly = (intervals) => mutations.run('weekly', {
    begin: () => {
      setWeeklyError(null)
      setWeeklyRefreshError(null)
      setWeeklyToast('')
    },
    request: () => api.replaceWeeklyAvailability(intervals),
    reconcile: (saved) => {
      if (saved) weekly.setData(saved)
    },
    reload: weekly.reload,
    success: () => setWeeklyToast('Horario semanal guardado.'),
    refreshFailure: () => setWeeklyRefreshError(new Error(
      'El horario se guardó, pero no pudimos actualizar la disponibilidad.',
    )),
    failure: setWeeklyError,
  })

  const createBlock = (body) => mutations.run('block-create', {
    begin: () => {
      setBlockError(null)
      setBlockRefreshError(null)
      setBlockToast('')
    },
    request: () => api.createBlock(body),
    reconcile: (saved) => {
      if (!saved) return
      blocks.setData((current) => [...(current ?? []), saved])
    },
    reload: blocks.reload,
    success: () => setBlockToast('Bloqueo creado.'),
    refreshFailure: () => setBlockRefreshError(new Error(
      'El bloqueo se guardó, pero no pudimos actualizar la lista.',
    )),
    failure: setBlockError,
  })

  const deleteBlock = (id) => {
    void mutations.run(`block-delete-${id}`, {
      begin: () => {
        setBlockError(null)
        setBlockRefreshError(null)
        setBlockToast('')
      },
      request: () => api.deleteBlock(id),
      reconcile: () => blocks.setData((current) =>
        current?.filter((block) => block.id !== id) ?? []),
      reload: blocks.reload,
      success: () => setBlockToast('Bloqueo eliminado.'),
      refreshFailure: () => setBlockRefreshError(new Error(
        'El bloqueo se eliminó, pero no pudimos actualizar la lista.',
      )),
      failure: setBlockError,
    })
  }

  if ((weekly.loading && !weekly.data) || (blocks.loading && !blocks.data)) {
    return <LoadingManagement label="Cargando disponibilidad" />
  }
  if (weekly.error && !weekly.data) return <ErrorManagement error={weekly.error} onRetry={weekly.reload} />
  if (blocks.error && !blocks.data) return <ErrorManagement error={blocks.error} onRetry={blocks.reload} />

  return (
    <div className="admin-management admin-availability">
      <StatusToast>{weeklyToast}</StatusToast>
      <StatusToast>{blockToast}</StatusToast>
      {weeklyRefreshError && (
        <ErrorManagement
          error={weeklyRefreshError}
          onRetry={retryWeeklyRefresh}
          retryLabel="Reintentar actualización"
        />
      )}
      {blockRefreshError && (
        <ErrorManagement
          error={blockRefreshError}
          onRetry={retryBlockRefresh}
          retryLabel="Reintentar actualización"
        />
      )}
      {weekly.error && !weeklyRefreshError && (
        <ErrorManagement error={weekly.error} onRetry={weekly.reload} />
      )}
      {blocks.error && !blockRefreshError && (
        <ErrorManagement error={blocks.error} onRetry={blocks.reload} />
      )}
      <WeeklyEditor
        key={JSON.stringify(weekly.data)}
        error={weeklyError}
        initial={weekly.data}
        pending={mutations.anyPending}
        onSave={saveWeekly}
      />
      <BlockEditor
        blocks={blocks.data}
        error={blockError}
        pending={mutations.anyPending}
        onCreate={createBlock}
        onDelete={deleteBlock}
      />
    </div>
  )
}

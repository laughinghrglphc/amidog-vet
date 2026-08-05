import { useMemo, useState } from 'react'
import { clinicTodayValue } from '../../../utils/petDates'
import useManagementResource from '../../hooks/useManagementResource'
import useMutationLifecycle from '../../hooks/useMutationLifecycle'
import { formatClinicDateTime } from '../../utils/date'
import {
  EmptyManagement,
  ErrorManagement,
  LoadingManagement,
  StatusToast,
} from '../management/ManagementState'

function petsQuery(filters, page) {
  const params = new URLSearchParams()
  if (filters.q.trim()) params.set('q', filters.q.trim())
  if (filters.active) params.set('active', filters.active)
  if (filters.clientId) params.set('clientId', filters.clientId)
  params.set('page', String(page))
  params.set('size', '25')
  return `?${params.toString()}`
}

function reloadFailed(result) {
  if (Array.isArray(result)) return result.some(reloadFailed)
  return result?.status === 'failure'
}

function PetDetail({
  api,
  id,
  onBack,
  onDashboardReload,
  onListReconcile,
  onListReload,
}) {
  const detail = useManagementResource(() => api.pet(id), [api, id])
  const [toast, setToast] = useState('')
  const [mutationError, setMutationError] = useState(null)
  const [refreshError, setRefreshError] = useState(null)
  const mutations = useMutationLifecycle()

  const reloadAffected = () => Promise.all([
    detail.reload(),
    onListReload(),
    onDashboardReload?.(),
  ])

  const retryRefresh = async () => {
    const results = await reloadAffected()
    if (!reloadFailed(results)) setRefreshError(null)
  }

  if (detail.loading && !detail.data) return <LoadingManagement label="Cargando mascota" />
  if (detail.error && !detail.data) return <ErrorManagement error={detail.error} onRetry={detail.reload} />
  if (!detail.data) return <EmptyManagement>No encontramos la mascota.</EmptyManagement>
  const pet = detail.data

  const submit = (event) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    void mutations.run('save', {
      begin: () => {
        setMutationError(null)
        setRefreshError(null)
        setToast('')
      },
      request: () => api.updatePet(id, {
        name: String(form.get('name')).trim(),
        species: String(form.get('species')).trim(),
        breed: String(form.get('breed')).trim() || null,
        birthdate: String(form.get('birthdate')) || null,
      }),
      reconcile: (saved) => {
        if (!saved) return
        detail.setData((current) => current ? { ...current, ...saved } : current)
        onListReconcile(saved)
      },
      reload: reloadAffected,
      success: () => setToast('Mascota actualizada.'),
      refreshFailure: (error) => setRefreshError(error),
      failure: setMutationError,
    })
  }

  return (
    <div className="admin-management">
      <StatusToast>{toast}</StatusToast>
      {mutationError && <ErrorManagement error={mutationError} />}
      {refreshError && (
        <ErrorManagement
          error={refreshError}
          onRetry={retryRefresh}
          retryLabel="Reintentar actualización"
        />
      )}
      <button type="button" className="is-secondary" disabled={mutations.anyPending} onClick={onBack}>Volver a mascotas</button>
      <form className="admin-management-form" onSubmit={submit}>
        <label>Nombre<input name="name" required maxLength="80" defaultValue={pet.name} /></label>
        <label>Especie<input name="species" required maxLength="40" defaultValue={pet.species} /></label>
        <label>Raza<input name="breed" maxLength="80" defaultValue={pet.breed ?? ''} /></label>
        <label>Fecha de nacimiento<input name="birthdate" type="date" max={clinicTodayValue()} defaultValue={pet.birthdate ?? ''} /></label>
        <label>Propietaria/o<input readOnly value={pet.ownerName} /></label>
        <label>Correo del propietario<input readOnly value={pet.ownerEmail} /></label>
        <p>Estado de archivo: {pet.active ? 'Activa' : 'Archivada'} (solo lectura)</p>
        <button type="submit" disabled={mutations.isPending('save')}>Guardar mascota</button>
      </form>
      <section className="admin-management-history">
        <h3>Historial de reservas</h3>
        <p>{pet.recentReservations.length} {pet.recentReservations.length === 1 ? 'reserva reciente' : 'reservas recientes'}</p>
        {pet.recentReservations.map((reservation) => (
          <p key={reservation.id}>
            {formatClinicDateTime(reservation.startsAt)}
            {' · '}
            {reservation.items.map((item) =>
              `${item.petName} · ${item.serviceName}`).join(' · ')}
          </p>
        ))}
      </section>
    </div>
  )
}

export default function PetManager({ api, onDashboardReload }) {
  const [filters, setFilters] = useState({ active: '', clientId: '', q: '' })
  const [applied, setApplied] = useState(filters)
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState(null)
  const query = useMemo(() => petsQuery(applied, page), [applied, page])
  const pets = useManagementResource(() => api.pets(query), [api, query])
  const reconcilePet = (saved) => pets.setData((current) => current
    ? {
        ...current,
        content: current.content.map((pet) =>
          pet.id === saved.id ? { ...pet, ...saved } : pet),
      }
    : current)

  if (selectedId) {
    return (
      <PetDetail
        api={api}
        id={selectedId}
        onBack={() => setSelectedId(null)}
        onDashboardReload={onDashboardReload}
        onListReconcile={reconcilePet}
        onListReload={pets.reload}
      />
    )
  }

  return (
    <div className="admin-management">
      <form className="admin-management-filters" onSubmit={(event) => {
        event.preventDefault()
        setPage(0)
        setApplied(filters)
      }}>
        <label>Buscar mascotas<input maxLength="120" type="search" value={filters.q} onChange={(event) => setFilters({ ...filters, q: event.target.value })} /></label>
        <label>Estado de la mascota<select value={filters.active} onChange={(event) => setFilters({ ...filters, active: event.target.value })}>
          <option value="">Todas</option><option value="true">Activas</option><option value="false">Archivadas</option>
        </select></label>
        <label>ID de cliente<input min="1" type="number" value={filters.clientId} onChange={(event) => setFilters({ ...filters, clientId: event.target.value })} /></label>
        <button type="submit">Aplicar filtros</button>
      </form>
      {pets.loading && <LoadingManagement label="Cargando mascotas" />}
      {pets.error && <ErrorManagement error={pets.error} onRetry={pets.reload} />}
      {!pets.loading && !pets.error && pets.data?.content.map((pet) => (
        <button className="admin-management-row" type="button" key={pet.id} onClick={() => setSelectedId(pet.id)}>
          <strong>{pet.name}</strong>
          <span>{pet.species}{pet.breed ? ` · ${pet.breed}` : ''} · {pet.ownerName}</span>
          <em>{pet.active ? 'Activa' : 'Archivada'}</em>
        </button>
      ))}
      {!pets.loading && !pets.error && !pets.data?.content.length && (
        <EmptyManagement>No hay mascotas para estos filtros.</EmptyManagement>
      )}
      {pets.data && (
        <nav className="admin-management-pagination" aria-label="Paginación de mascotas">
          <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Anterior</button>
          <span>Página {pets.data.page + 1} de {Math.max(1, pets.data.totalPages)}</span>
          <button type="button" disabled={page + 1 >= pets.data.totalPages} onClick={() => setPage((value) => value + 1)}>Siguiente</button>
        </nav>
      )}
    </div>
  )
}

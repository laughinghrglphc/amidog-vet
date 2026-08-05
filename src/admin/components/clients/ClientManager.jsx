import { useMemo, useState } from 'react'
import useManagementResource from '../../hooks/useManagementResource'
import useMutationLifecycle from '../../hooks/useMutationLifecycle'
import { formatClinicDateTime } from '../../utils/date'
import {
  EmptyManagement,
  ErrorManagement,
  LoadingManagement,
  StatusToast,
} from '../management/ManagementState'

function clientsQuery(filters, page) {
  const params = new URLSearchParams()
  if (filters.q.trim()) params.set('q', filters.q.trim())
  if (filters.active) params.set('active', filters.active)
  params.set('page', String(page))
  params.set('size', '25')
  return `?${params.toString()}`
}

function reloadFailed(result) {
  if (Array.isArray(result)) return result.some(reloadFailed)
  return result?.status === 'failure'
}

function ClientDetail({
  api,
  id,
  onBack,
  onDashboardReload,
  onListReconcile,
  onListReload,
}) {
  const detail = useManagementResource(() => api.client(id), [api, id])
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

  if (detail.loading && !detail.data) return <LoadingManagement label="Cargando cliente" />
  if (detail.error && !detail.data) return <ErrorManagement error={detail.error} onRetry={detail.reload} />
  if (!detail.data) return <EmptyManagement>No encontramos al cliente.</EmptyManagement>
  const client = detail.data

  const submit = (event) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    void mutations.run('save', {
      begin: () => {
        setMutationError(null)
        setRefreshError(null)
        setToast('')
      },
      request: () => api.updateClient(id, {
        name: String(form.get('name')).trim(),
        phone: String(form.get('phone')).trim(),
        active: form.get('active') === 'on',
      }),
      reconcile: (saved) => {
        if (!saved) return
        detail.setData((current) => current ? { ...current, ...saved } : current)
        onListReconcile(saved)
      },
      reload: reloadAffected,
      success: () => setToast('Cliente actualizado.'),
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
      <button type="button" className="is-secondary" disabled={mutations.anyPending} onClick={onBack}>Volver a clientes</button>
      <form className="admin-management-form" onSubmit={submit}>
        <label>Nombre<input name="name" required maxLength="120" defaultValue={client.name} /></label>
        <label>Teléfono<input name="phone" required maxLength="30" defaultValue={client.phone} /></label>
        <label>Correo<input readOnly value={client.email} /></label>
        <label><input name="active" type="checkbox" defaultChecked={client.active} /> Cuenta activa</label>
        <button type="submit" disabled={mutations.isPending('save')}>Guardar cliente</button>
      </form>
      <section className="admin-management-history">
        <h3>Resumen de reservas</h3>
        <p>{client.reservationCounts.total} reservas · {client.reservationCounts.upcoming} próximas</p>
        <p>Pendientes: {client.reservationCounts.pending} · Confirmadas: {client.reservationCounts.confirmed} · Completadas: {client.reservationCounts.completed}</p>
        <p>Canceladas: {client.reservationCounts.cancelled} · Inasistencias: {client.reservationCounts.noShow}</p>
      </section>
      <section className="admin-management-history">
        <h3>Próximas reservas</h3>
        {client.upcomingReservations.map((reservation) => (
          <article key={reservation.id}>
            <p>
              {reservation.items.map((item) =>
                `${item.petName} · ${item.serviceName}`).join(' · ')}
            </p>
            <small>
              Inicio: {formatClinicDateTime(reservation.startsAt)}
              {' · '}Fin: {formatClinicDateTime(reservation.endsAt)}
            </small>
          </article>
        ))}
        {!client.upcomingReservations.length && <p>Sin reservas próximas.</p>}
      </section>
      <section className="admin-management-history">
        <h3>Mascotas</h3>
        {client.pets.map((pet) => <p key={pet.id}>{pet.name} · {pet.species} · {pet.active ? 'Activa' : 'Archivada'}</p>)}
      </section>
    </div>
  )
}

export default function ClientManager({ api, onDashboardReload }) {
  const [filters, setFilters] = useState({ active: '', q: '' })
  const [applied, setApplied] = useState(filters)
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState(null)
  const query = useMemo(() => clientsQuery(applied, page), [applied, page])
  const clients = useManagementResource(() => api.clients(query), [api, query])
  const reconcileClient = (saved) => clients.setData((current) => current
    ? {
        ...current,
        content: current.content.map((client) =>
          client.id === saved.id ? { ...client, ...saved } : client),
      }
    : current)

  if (selectedId) {
    return (
      <ClientDetail
        api={api}
        id={selectedId}
        onBack={() => setSelectedId(null)}
        onDashboardReload={onDashboardReload}
        onListReconcile={reconcileClient}
        onListReload={clients.reload}
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
        <label>Buscar clientes<input maxLength="120" type="search" value={filters.q} onChange={(event) => setFilters({ ...filters, q: event.target.value })} /></label>
        <label>Estado del cliente<select value={filters.active} onChange={(event) => setFilters({ ...filters, active: event.target.value })}>
          <option value="">Todos</option>
          <option value="true">Activos</option>
          <option value="false">Inactivos</option>
        </select></label>
        <button type="submit">Aplicar filtros</button>
      </form>
      {clients.loading && <LoadingManagement label="Cargando clientes" />}
      {clients.error && <ErrorManagement error={clients.error} onRetry={clients.reload} />}
      {!clients.loading && !clients.error && clients.data?.content.map((client) => (
        <button className="admin-management-row" type="button" key={client.id} onClick={() => setSelectedId(client.id)}>
          <strong>{client.name}</strong>
          <span>{client.email} · {client.phone}</span>
          <em>{client.active ? 'Activo' : 'Inactivo'}</em>
        </button>
      ))}
      {!clients.loading && !clients.error && !clients.data?.content.length && (
        <EmptyManagement>No hay clientes para estos filtros.</EmptyManagement>
      )}
      {clients.data && (
        <nav className="admin-management-pagination" aria-label="Paginación de clientes">
          <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Anterior</button>
          <span>Página {clients.data.page + 1} de {Math.max(1, clients.data.totalPages)}</span>
          <button type="button" disabled={page + 1 >= clients.data.totalPages} onClick={() => setPage((value) => value + 1)}>Siguiente</button>
        </nav>
      )}
    </div>
  )
}

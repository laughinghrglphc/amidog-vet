import { useState } from 'react'
import useManagementResource from '../../hooks/useManagementResource'
import useMutationLifecycle from '../../hooks/useMutationLifecycle'
import {
  EmptyManagement,
  ErrorManagement,
  LoadingManagement,
  StatusToast,
} from '../management/ManagementState'

const emptyDraft = {
  code: '',
  description: '',
  displayOrder: 0,
  name: '',
}

function ServiceForm({ error, pending, service, onCancel, onSave }) {
  const [draft, setDraft] = useState(service ?? emptyDraft)
  const editing = Boolean(service)

  const submit = (event) => {
    event.preventDefault()
    void onSave({
      ...(editing ? {} : { code: draft.code.trim() }),
      name: draft.name.trim(),
      description: draft.description.trim() || null,
      displayOrder: Number(draft.displayOrder),
      ...(editing ? { active: service.active } : {}),
    })
  }

  return (
    <form className="admin-management-form" onSubmit={submit}>
      {error && <ErrorManagement error={error} />}
      <label>
        Código
        <input
          required
          maxLength="60"
          pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
          readOnly={editing}
          value={draft.code}
          onChange={(event) => setDraft({ ...draft, code: event.target.value })}
        />
      </label>
      <label>Nombre<input required maxLength="100" value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></label>
      <label>Descripción<textarea maxLength="500" value={draft.description ?? ''} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
      <label>Orden<input required min="0" type="number" value={draft.displayOrder} onChange={(event) => setDraft({ ...draft, displayOrder: event.target.value })} /></label>
      <div className="admin-modal-actions">
        <button type="button" className="is-secondary" disabled={pending} onClick={onCancel}>Volver</button>
        <button type="submit" disabled={pending}>
          {editing ? 'Guardar cambios' : 'Guardar servicio'}
        </button>
      </div>
    </form>
  )
}

export default function ServiceManager({ api }) {
  const services = useManagementResource(() => api.services(), [api])
  const [editing, setEditing] = useState(undefined)
  const [toast, setToast] = useState('')
  const [mutationError, setMutationError] = useState(null)
  const [refreshWarning, setRefreshWarning] = useState(null)
  const mutations = useMutationLifecycle()

  const upsertService = (saved) => {
    if (!saved) return
    services.setData((current) => {
      const list = current ?? []
      const exists = list.some((service) => service.id === saved.id)
      return exists
        ? list.map((service) => service.id === saved.id ? saved : service)
        : [...list, saved]
    })
  }

  const retryRefresh = async () => {
    const result = await services.reload()
    if (result.status === 'success') setRefreshWarning(null)
  }

  const save = (body) => {
    const current = editing
    return mutations.run('save', {
      begin: () => {
        setMutationError(null)
        setRefreshWarning(null)
        setToast('')
      },
      request: () => current
        ? api.updateService(current.id, body)
        : api.createService(body),
      reconcile: upsertService,
      reload: services.reload,
      success: () => {
        setEditing(undefined)
        setToast(current ? 'Servicio actualizado.' : 'Servicio creado.')
      },
      refreshFailure: () => {
        setEditing(undefined)
        setRefreshWarning(new Error(
          'El servicio se guardó, pero no pudimos actualizar la lista.',
        ))
      },
      failure: setMutationError,
    })
  }

  const archive = (service) => {
    void mutations.run(`archive-${service.id}`, {
      begin: () => {
        setMutationError(null)
        setRefreshWarning(null)
        setToast('')
      },
      request: () => api.archiveService(service.id),
      reconcile: () => services.setData((current) =>
        current?.map((item) => item.id === service.id
          ? { ...item, active: false }
          : item) ?? []),
      reload: services.reload,
      success: () => setToast('Servicio archivado. Su historial permanece disponible.'),
      refreshFailure: () => setRefreshWarning(new Error(
        'El servicio se archivó, pero no pudimos actualizar la lista.',
      )),
      failure: setMutationError,
    })
  }

  const reactivate = (service) => {
    void mutations.run(`reactivate-${service.id}`, {
      begin: () => {
        setMutationError(null)
        setRefreshWarning(null)
        setToast('')
      },
      request: () => api.updateService(service.id, {
        name: service.name,
        description: service.description,
        displayOrder: service.displayOrder,
        active: true,
      }),
      reconcile: upsertService,
      reload: services.reload,
      success: () => setToast('Servicio reactivado.'),
      refreshFailure: () => setRefreshWarning(new Error(
        'El servicio se guardó, pero no pudimos actualizar la lista.',
      )),
      failure: setMutationError,
    })
  }

  if (editing !== undefined) {
    return (
      <ServiceForm
        error={mutationError}
        pending={mutations.anyPending}
        service={editing}
        onCancel={() => setEditing(undefined)}
        onSave={save}
      />
    )
  }

  return (
    <div className="admin-management">
      <StatusToast>{toast}</StatusToast>
      {mutationError && <ErrorManagement error={mutationError} />}
      {refreshWarning && (
        <ErrorManagement
          error={refreshWarning}
          onRetry={retryRefresh}
          retryLabel="Reintentar actualización"
        />
      )}
      <div className="admin-management-toolbar">
        <button type="button" disabled={mutations.anyPending} onClick={() => setEditing(null)}>Crear servicio</button>
      </div>
      {services.loading && <LoadingManagement label="Cargando servicios" />}
      {services.error && !refreshWarning && (
        <ErrorManagement error={services.error} onRetry={services.reload} />
      )}
      {!services.loading && services.data?.map((service) => (
        <article className="admin-management-item" key={service.id}>
          <div>
            <strong>{service.name}</strong>
            <small>{service.code} · orden {service.displayOrder}</small>
            <p>{service.description || 'Sin descripción'}</p>
            <em>{service.active ? 'Activo' : 'Archivado'}</em>
          </div>
          <div className="admin-management-item__actions">
            <button type="button" disabled={mutations.anyPending} aria-label={`Editar ${service.name}`} onClick={() => setEditing(service)}>Editar</button>
            {service.active ? (
              <button type="button" disabled={mutations.anyPending} aria-label={`Archivar ${service.name}`} onClick={() => archive(service)}>Archivar</button>
            ) : (
              <button type="button" disabled={mutations.anyPending} aria-label={`Reactivar ${service.name}`} onClick={() => reactivate(service)}>Reactivar</button>
            )}
          </div>
        </article>
      ))}
      {!services.loading && !services.error && !services.data?.length && (
        <EmptyManagement>No hay servicios configurados.</EmptyManagement>
      )}
    </div>
  )
}

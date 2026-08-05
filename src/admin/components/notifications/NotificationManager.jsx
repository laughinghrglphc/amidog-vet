import { useState } from 'react'
import useMutationLifecycle from '../../hooks/useMutationLifecycle'
import {
  EmptyManagement,
  ErrorManagement,
  LoadingManagement,
  StatusToast,
} from '../management/ManagementState'

export default function NotificationManager({ api, notifications }) {
  const [mutationError, setMutationError] = useState(null)
  const [refreshError, setRefreshError] = useState(null)
  const [toast, setToast] = useState('')
  const mutations = useMutationLifecycle()

  const retryRefresh = async () => {
    const result = await notifications.reload()
    if (result.status === 'success') setRefreshError(null)
  }

  const read = (id) => {
    void mutations.run(`read-${id}`, {
      begin: () => {
        setMutationError(null)
        setRefreshError(null)
        setToast('')
      },
      request: () => api.readNotification(id),
      reconcile: (saved) => {
        if (!saved) return
        notifications.setData((current) => current?.map((notification) =>
          notification.id === saved.id ? saved : notification) ?? [])
      },
      reload: notifications.reload,
      refreshFailure: () => setRefreshError(new Error(
        'La notificación se guardó, pero no pudimos actualizar la lista.',
      )),
      success: () => setToast('Notificación marcada como leída.'),
      failure: setMutationError,
    })
  }

  const readAll = () => {
    void mutations.run('read-all', {
      begin: () => {
        setMutationError(null)
        setRefreshError(null)
        setToast('')
      },
      request: api.readAllNotifications,
      reconcile: () => notifications.setData((current) =>
        current?.map((notification) => ({
          ...notification,
          unread: false,
        })) ?? []),
      reload: notifications.reload,
      refreshFailure: () => setRefreshError(new Error(
        'Las notificaciones se guardaron, pero no pudimos actualizar la lista.',
      )),
      success: (result) => setToast(
        `${result.markedRead} notificaciones marcadas como leídas.`,
      ),
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
      <div className="admin-management-toolbar">
        <button
          type="button"
          disabled={mutations.anyPending || !notifications.data?.some((item) => item.unread)}
          onClick={readAll}
        >
          Marcar todas como leídas
        </button>
      </div>
      {notifications.loading && <LoadingManagement label="Cargando notificaciones" />}
      {notifications.error && !refreshError && (
        <ErrorManagement error={notifications.error} onRetry={notifications.reload} />
      )}
      {!notifications.loading && notifications.data?.map((notification) => (
        <article
          className={`admin-management-item ${notification.unread ? 'is-unread' : ''}`}
          key={notification.id}
        >
          <div>
            <strong>{notification.title}</strong>
            <p>{notification.body}</p>
            <small>
              {new Intl.DateTimeFormat('es-CL', {
                dateStyle: 'medium',
                timeStyle: 'short',
                timeZone: 'America/Santiago',
              }).format(new Date(notification.createdAt))}
              {notification.reservationId ? ` · Reserva ${notification.reservationId}` : ''}
            </small>
          </div>
          {notification.unread && (
            <button
              type="button"
              disabled={mutations.anyPending}
              aria-label={`Marcar ${notification.title} como leída`}
              onClick={() => read(notification.id)}
            >
              Marcar como leída
            </button>
          )}
        </article>
      ))}
      {!notifications.loading && !notifications.error && !notifications.data?.length && (
        <EmptyManagement>No hay notificaciones operacionales.</EmptyManagement>
      )}
    </div>
  )
}

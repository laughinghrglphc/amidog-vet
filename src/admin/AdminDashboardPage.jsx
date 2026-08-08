import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { safeAuthError } from '../auth/authError'
import { useAuth } from '../auth/useAuth'
import { adminApi } from '../api/adminApi'
import { bookingApi } from '../api/bookingApi'
import useAdminDashboard from './hooks/useAdminDashboard'
import PanelAdministrador from './PanelAdministrador'

function LoadingState() {
  return (
    <main className="admin-state" aria-busy="true" aria-live="polite">
      <div className="admin-state__spinner" aria-hidden="true" />
      <h1>Cargando panel</h1>
      <p>Estamos preparando la información de AmiDog.</p>
    </main>
  )
}

function ErrorState({ message, onRetry }) {
  return (
    <main className="admin-state" role="alert">
      <h1>No pudimos cargar el panel</h1>
      <p>{message || 'Revisa tu conexión e inténtalo nuevamente.'}</p>
      <button type="button" onClick={onRetry}>Reintentar</button>
    </main>
  )
}

export default function AdminDashboardPage({
  api = adminApi,
  availabilityApi = bookingApi,
  now = new Date(),
}) {
  const auth = useAuth()
  const navigate = useNavigate()
  const dashboard = useAdminDashboard(api)
  const [logoutError, setLogoutError] = useState('')
  const [logoutPending, setLogoutPending] = useState(false)
  const logoutAttemptRef = useRef(0)
  const mountedRef = useRef(false)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      logoutAttemptRef.current += 1
    }
  }, [])

  const handleLogout = async () => {
    const attempt = ++logoutAttemptRef.current
    setLogoutError('')
    setLogoutPending(true)
    try {
      const completed = await auth.logout()
      if (
        mountedRef.current
        && attempt === logoutAttemptRef.current
        && completed
      ) {
        navigate('/login', { replace: true })
      }
    } catch (requestError) {
      if (mountedRef.current && attempt === logoutAttemptRef.current) {
        setLogoutError(safeAuthError(
          requestError,
          'No pudimos cerrar la sesión. Intenta nuevamente.',
        ).message)
      }
    } finally {
      if (mountedRef.current && attempt === logoutAttemptRef.current) {
        setLogoutPending(false)
      }
    }
  }

  if (dashboard.loading && !dashboard.data) return <LoadingState />
  if (dashboard.error && !dashboard.data) {
    return (
      <ErrorState
        message={dashboard.error.message}
        onRetry={dashboard.reload}
      />
    )
  }

  return (
    <PanelAdministrador
      api={api}
      availabilityApi={availabilityApi}
      data={dashboard.data}
      error={logoutError}
      logoutPending={logoutPending}
      now={now}
      onLogout={handleLogout}
      onReloadDashboard={dashboard.reload}
    />
  )
}

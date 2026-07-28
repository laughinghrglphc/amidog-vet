import { Navigate, useLocation } from 'react-router'
import { safeAuthError } from './authError'
import { homeForRole } from './authNavigation'
import { useAuth } from './useAuth'

export function RequireRole({ children, role }) {
  const { error, loading, refresh, user } = useAuth()
  const location = useLocation()

  if (loading) {
    return <p className="route-status" role="status">Verificando sesión…</p>
  }

  if (error) {
    const feedback = safeAuthError(
      error,
      'No pudimos verificar tu sesión. Intenta nuevamente.',
    )
    return (
      <section className="route-error" aria-labelledby="session-error-title">
        <h1 id="session-error-title">No pudimos verificar tu sesión</h1>
        <p role="alert">{feedback.message}</p>
        <button type="button" onClick={() => void refresh()}>Reintentar</button>
      </section>
    )
  }

  if (!user) {
    const next = `${location.pathname}${location.search}${location.hash}`
    return (
      <Navigate
        replace
        to={`/login?next=${encodeURIComponent(next)}`}
      />
    )
  }

  if (user.accountType !== role) {
    return <Navigate replace to={homeForRole(user.accountType)} />
  }

  return children
}

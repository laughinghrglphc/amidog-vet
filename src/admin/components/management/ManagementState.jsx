export function LoadingManagement({ label = 'Cargando información' }) {
  return <p className="admin-management-state" aria-busy="true">{label}…</p>
}

export function EmptyManagement({ children }) {
  return <p className="admin-management-state">{children}</p>
}

export function ErrorManagement({ error, onRetry, retryLabel = 'Reintentar' }) {
  return (
    <div className="admin-management-error" role="alert">
      <p>{error?.message || 'No pudimos cargar esta información.'}</p>
      {onRetry && <button type="button" onClick={onRetry}>{retryLabel}</button>}
    </div>
  )
}

export function StatusToast({ children }) {
  if (!children) return null
  return <p className="admin-management-toast" role="status">{children}</p>
}

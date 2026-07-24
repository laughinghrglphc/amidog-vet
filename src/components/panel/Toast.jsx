function Toast({ message }) {
  return <div className={`toast ${message ? 'is-visible' : ''}`} role="status" aria-live="polite">{message}</div>
}

export default Toast

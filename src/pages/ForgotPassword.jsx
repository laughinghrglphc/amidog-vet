import { useRef, useState } from 'react'
import { Link } from 'react-router'
import { authApi } from '../api/authApi'
import { ErrorFeedback } from '../auth/ErrorFeedback'
import { safeAuthError } from '../auth/authError'
import AccountPage from '../components/AccountPage'

export default function ForgotPassword({ api = authApi }) {
  const [error, setError] = useState(null)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const submittingRef = useRef(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    const form = new FormData(event.currentTarget)
    submittingRef.current = true
    setSubmitting(true)
    setError(null)
    setMessage('')
    try {
      const response = await api.forgotPassword(
        String(form.get('email') ?? ''),
      )
      setMessage(response.message)
    } catch (requestError) {
      setError(safeAuthError(
        requestError,
        'No pudimos solicitar el cambio de contraseña. Intenta nuevamente.',
      ))
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <AccountPage
      heading="Recuperar contraseña"
      intro="Te enviaremos instrucciones si existe una cuenta con ese correo."
    >
      <ErrorFeedback error={error} />
      {message && (
        <p className="account-feedback account-feedback--success" role="status">
          {message}
        </p>
      )}
      <form className="account-form" noValidate onSubmit={handleSubmit}>
        <div className="form-group">
          <label className="form-group__label" htmlFor="recovery-email">Correo electrónico</label>
          <input id="recovery-email" name="email" type="email" autoComplete="email" required />
        </div>
        <button className="btn-primary" type="submit" disabled={submitting}>
          {submitting ? 'Enviando…' : 'Enviar instrucciones'}
        </button>
      </form>
      <p className="account-card__footer"><Link to="/login">Volver a iniciar sesión</Link></p>
    </AccountPage>
  )
}

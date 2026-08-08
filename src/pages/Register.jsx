import { useRef, useState } from 'react'
import { Link } from 'react-router'
import { authApi } from '../api/authApi'
import { ErrorFeedback } from '../auth/ErrorFeedback'
import { safeAuthError } from '../auth/authError'
import AccountPage from '../components/AccountPage'

function clientValidation(password, confirmation) {
  if (password.length < 12 || password.length > 128) {
    return 'La contraseña debe tener entre 12 y 128 caracteres.'
  }
  if (password !== confirmation) {
    return 'Las contraseñas no coinciden.'
  }
  return ''
}

export default function Register({ api = authApi }) {
  const [error, setError] = useState(null)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const submittingRef = useRef(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }

    const formElement = event.currentTarget
    const form = new FormData(formElement)
    const password = String(form.get('password') ?? '')
    const confirmation = String(form.get('confirmation') ?? '')
    const validationMessage = clientValidation(password, confirmation)
    if (validationMessage) {
      setError({ errors: {}, message: validationMessage })
      return
    }

    submittingRef.current = true
    setSubmitting(true)
    setError(null)
    setMessage('')

    try {
      const response = await api.register({
        email: String(form.get('email') ?? ''),
        password,
        name: String(form.get('name') ?? ''),
        phone: String(form.get('phone') ?? ''),
      })
      setMessage(response.message)
      formElement.reset()
    } catch (requestError) {
      setError(safeAuthError(
        requestError,
        'No pudimos crear la cuenta. Intenta nuevamente.',
      ))
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <AccountPage
      heading="Crear cuenta"
      intro="Crea tu cuenta para reservar horas y registrar tus mascotas."
    >
      <ErrorFeedback error={error} />
      {message && (
        <p className="account-feedback account-feedback--success" role="status">
          {message}
        </p>
      )}
      <form className="account-form" noValidate onSubmit={handleSubmit}>
        <div className="form-group">
          <label className="form-group__label" htmlFor="register-name">Nombre completo</label>
          <input id="register-name" name="name" type="text" autoComplete="name" required />
        </div>
        <div className="form-group">
          <label className="form-group__label" htmlFor="register-phone">Teléfono</label>
          <input id="register-phone" name="phone" type="tel" autoComplete="tel" required />
        </div>
        <div className="form-group">
          <label className="form-group__label" htmlFor="register-email">Correo electrónico</label>
          <input id="register-email" name="email" type="email" autoComplete="email" required />
        </div>
        <div className="form-group">
          <label className="form-group__label" htmlFor="register-password">Contraseña</label>
          <input id="register-password" name="password" type="password" autoComplete="new-password" minLength="12" required />
          <small>Usa entre 12 y 128 caracteres.</small>
        </div>
        <div className="form-group">
          <label className="form-group__label" htmlFor="register-confirmation">Confirmar contraseña</label>
          <input id="register-confirmation" name="confirmation" type="password" autoComplete="new-password" required />
        </div>
        <button className="btn-primary" type="submit" disabled={submitting}>
          {submitting ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
      </form>
      <p className="account-card__footer">
        ¿Ya tienes una cuenta? <Link to="/login">Iniciar sesión</Link>
      </p>
    </AccountPage>
  )
}

import { useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { authApi } from '../api/authApi'
import { ErrorFeedback } from '../auth/ErrorFeedback'
import { safeAuthError } from '../auth/authError'
import AccountPage from '../components/AccountPage'

function validatePasswords(password, confirmation) {
  if (password.length < 12 || password.length > 128) {
    return 'La contraseña debe tener entre 12 y 128 caracteres.'
  }
  if (password !== confirmation) {
    return 'Las contraseñas no coinciden.'
  }
  return ''
}

export default function ResetPassword({ api = authApi }) {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')
  const [error, setError] = useState(
    token
      ? null
      : {
          errors: {},
          message: 'El enlace para restablecer la contraseña no es válido.',
        },
  )
  const [submitting, setSubmitting] = useState(false)
  const submittingRef = useRef(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (!token || submittingRef.current) {
      return
    }
    const form = new FormData(event.currentTarget)
    const password = String(form.get('password') ?? '')
    const confirmation = String(form.get('confirmation') ?? '')
    const validationMessage = validatePasswords(password, confirmation)
    if (validationMessage) {
      setError({ errors: {}, message: validationMessage })
      return
    }

    submittingRef.current = true
    setSubmitting(true)
    setError(null)
    let completed = false
    try {
      await api.resetPassword(token, password)
      completed = true
      navigate('/login?reset=1', { replace: true })
    } catch (requestError) {
      setError(safeAuthError(
        requestError,
        'No pudimos restablecer la contraseña. Solicita un enlace nuevo.',
      ))
    } finally {
      if (!completed) {
        submittingRef.current = false
        setSubmitting(false)
      }
    }
  }

  return (
    <AccountPage
      heading="Restablecer contraseña"
      intro="Elige una nueva contraseña para tu cuenta."
    >
      <ErrorFeedback error={error} />
      {token && (
        <form className="account-form" noValidate onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-group__label" htmlFor="reset-password">Nueva contraseña</label>
            <input id="reset-password" name="password" type="password" autoComplete="new-password" minLength="12" required />
          </div>
          <div className="form-group">
            <label className="form-group__label" htmlFor="reset-confirmation">Confirmar nueva contraseña</label>
            <input id="reset-confirmation" name="confirmation" type="password" autoComplete="new-password" required />
          </div>
          <button className="btn-primary" type="submit" disabled={submitting}>
            {submitting ? 'Guardando…' : 'Guardar nueva contraseña'}
          </button>
        </form>
      )}
    </AccountPage>
  )
}

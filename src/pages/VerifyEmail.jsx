import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { authApi } from '../api/authApi'
import { ErrorFeedback } from '../auth/ErrorFeedback'
import { safeAuthError } from '../auth/authError'
import AccountPage from '../components/AccountPage'

export default function VerifyEmail({ api = authApi }) {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')
  const startedRef = useRef(false)
  const mountedRef = useRef(false)
  const resendPendingRef = useRef(false)
  const [verifying, setVerifying] = useState(Boolean(token))
  const [resending, setResending] = useState(false)
  const [error, setError] = useState(
    token
      ? null
      : {
          errors: {},
          message: 'El enlace de verificación no es válido. Solicita uno nuevo.',
        },
  )
  const [message, setMessage] = useState('')

  useEffect(() => {
    mountedRef.current = true
    if (token && !startedRef.current) {
      startedRef.current = true
      void api.verifyEmail(token)
        .then(() => {
          if (mountedRef.current) {
            navigate('/login?verified=1', { replace: true })
          }
        })
        .catch((requestError) => {
          if (mountedRef.current) {
            setError(safeAuthError(
              requestError,
              'No pudimos verificar el correo. Solicita un enlace nuevo.',
            ))
            setVerifying(false)
          }
        })
    }

    return () => {
      mountedRef.current = false
    }
  }, [api, navigate, token])

  const handleResend = async (event) => {
    event.preventDefault()
    if (resendPendingRef.current) {
      return
    }
    const form = new FormData(event.currentTarget)
    resendPendingRef.current = true
    setResending(true)
    setError(null)
    setMessage('')
    try {
      const response = await api.resendVerification(
        String(form.get('email') ?? ''),
      )
      setMessage(response.message)
    } catch (requestError) {
      setError(safeAuthError(
        requestError,
        'No pudimos reenviar la verificación. Intenta nuevamente.',
      ))
    } finally {
      resendPendingRef.current = false
      setResending(false)
    }
  }

  return (
    <AccountPage
      heading="Verificar correo"
      intro="La verificación protege tu cuenta antes de reservar."
    >
      {verifying && <p role="status">Verificando tu correo…</p>}
      <ErrorFeedback error={error} />
      {message && (
        <p className="account-feedback account-feedback--success" role="status">
          {message}
        </p>
      )}
      {!verifying && (
        <form className="account-form" noValidate onSubmit={handleResend}>
          <div className="form-group">
            <label className="form-group__label" htmlFor="verification-email">
              Correo electrónico
            </label>
            <input id="verification-email" name="email" type="email" autoComplete="email" required />
          </div>
          <button className="btn-primary" type="submit" disabled={resending}>
            {resending ? 'Reenviando…' : 'Reenviar verificación'}
          </button>
        </form>
      )}
    </AccountPage>
  )
}

import { useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { ErrorFeedback } from '../auth/ErrorFeedback'
import { safeAuthError } from '../auth/authError'
import { safeNextPath } from '../auth/authNavigation'
import { useAuth } from '../auth/useAuth'
import Header from '../components/Header'
import Footer from '../components/Footer'

export default function Login() {
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const submittingRef = useRef(false)
  const { login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const notice = new URLSearchParams(location.search)

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }

    const form = new FormData(event.currentTarget)
    const email = String(form.get('email') ?? '')
    const password = String(form.get('password') ?? '')
    submittingRef.current = true
    setSubmitting(true)
    setError(null)

    try {
      const authenticatedUser = await login(email, password)
      if (!authenticatedUser) {
        return
      }
      const destination = safeNextPath(
        notice.get('next'),
        authenticatedUser.accountType,
      )
      navigate(destination, { replace: true })
    } catch (requestError) {
      setError(safeAuthError(
        requestError,
        'No pudimos iniciar sesión. Intenta nuevamente.',
      ))
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <>
      <Header />
      <main className="login-page">
        <div className="login-page__panel">
          <div className="login-page__content">
            <div className="badge">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
              </svg>
              INGRESO
            </div>
            <h1 className="welcome__heading">
              <img src="/assets/img/patitas.png" alt="" className="patita-izq" aria-hidden="true" />
              Bienvenido<br />
              <span className="highlight">a AmiDog<img src="/assets/img/patitas.png" alt="" className="patita-der" aria-hidden="true" /></span>
            </h1>
            <p className="welcome__description">Ingresa para gestionar tus reservas y los datos de tus mascotas.</p>
            <ul className="welcome__features">
              <li className="feature-item">
                <span className="feature-item__icon" aria-hidden="true">✓</span>
                Reserva tus horas fácilmente
              </li>
              <li className="feature-item">
                <span className="feature-item__icon" aria-hidden="true">+</span>
                Registra a tus mascotas
              </li>
              <li className="feature-item">
                <span className="feature-item__icon" aria-hidden="true">⌚</span>
                Revisa tus próximas atenciones
              </li>
            </ul>
          </div>
          <div className="login-page__form-card">
            <div className="login-card">
              <h2 className="login-card__title">Iniciar sesión</h2>
              <p className="login-card__subtitle">Accede a tu cuenta de AmiDog.</p>
              {notice.get('verified') === '1' && (
                <p className="account-feedback account-feedback--success" role="status">
                  Tu correo fue verificado. Ya puedes iniciar sesión.
                </p>
              )}
              {notice.get('reset') === '1' && (
                <p className="account-feedback account-feedback--success" role="status">
                  Tu contraseña fue actualizada. Ya puedes iniciar sesión.
                </p>
              )}
              <ErrorFeedback error={error} />
              <form noValidate onSubmit={handleSubmit}>
                <div className="form-group">
                  <label className="form-group__label" htmlFor="email">Correo electrónico</label>
                  <div className="input-wrapper">
                    <span className="input-wrapper__icon" aria-hidden="true">@</span>
                    <input type="email" id="email" name="email" placeholder="nombre@correo.cl" autoComplete="email" required />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-group__label" htmlFor="password">Contraseña</label>
                  <div className="input-wrapper">
                    <span className="input-wrapper__icon" aria-hidden="true">●</span>
                    <input type={showPassword ? 'text' : 'password'} id="password" name="password" placeholder="••••••••••••" autoComplete="current-password" required />
                    <button type="button" className="input-wrapper__icon--right" aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'} onClick={() => setShowPassword((current) => !current)}>
                      {showPassword ? 'Ocultar' : 'Mostrar'}
                    </button>
                  </div>
                </div>
                <div className="form-row form-row--end">
                  <Link to="/olvide-contrasena" className="forgot-link">¿Olvidaste tu contraseña?</Link>
                </div>
                <button type="submit" className="btn-primary" disabled={submitting}>
                  {submitting ? 'Ingresando…' : 'Ingresar'}
                </button>
              </form>
              <div className="divider" aria-hidden="true"><span className="divider__dot" /></div>
              <p className="form-footer-text">¿Aún no tienes una cuenta?</p>
              <Link to="/crear-cuenta" className="btn-secondary">Crear cuenta</Link>
            </div>
          </div>
          <div className="login-page__visual">
            <div className="animal-visual">
              <div className="mascota-circular">
                <div className="mascota-circular__bg" />
                <img src="/assets/img/AnimalesPrincipales.png" alt="Mascotas AmiDog: perro y gatos" className="mascota-circular__img" />
                <img src="/assets/img/estrella.png" alt="" className="mascota-circular__star mascota-circular__star--top" aria-hidden="true" />
                <img src="/assets/img/estrella.png" alt="" className="mascota-circular__star mascota-circular__star--bottom" aria-hidden="true" />
                <img src="/assets/img/patitas.png" alt="" className="mascota-circular__paw" aria-hidden="true" />
              </div>
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}

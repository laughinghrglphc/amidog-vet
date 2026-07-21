import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Header from '../components/Header'
import Footer from '../components/Footer'

export default function Login() {
  const [showPassword, setShowPassword] = useState(false)
  const navigate = useNavigate()

  const handleSubmit = (event) => {
    event.preventDefault()
    navigate('/panel')
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
            <p className="welcome__description">Ingresa para gestionar tus reservas,<br />consultas y datos de tus mascotas.</p>
            <ul className="welcome__features">
              <li className="feature-item">
                <span className="feature-item__icon" aria-hidden="true"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg></span>
                Reserva tus horas fácilmente
              </li>
              <li className="feature-item">
                <span className="feature-item__icon" aria-hidden="true"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg></span>
                Registra a tus mascotas
              </li>
              <li className="feature-item">
                <span className="feature-item__icon" aria-hidden="true"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="9" /><polyline points="12 7 12 12 15 15" /></svg></span>
                Revisa tus próximas atenciones
              </li>
            </ul>
          </div>
          <div className="login-page__form-card">
            <div className="login-card">
              <h2 className="login-card__title">Iniciar Sesión</h2>
              <p className="login-card__subtitle">Accede a tu cuenta de AmiDog.</p>
              <form noValidate onSubmit={handleSubmit}>
                <div className="form-group">
                  <label className="form-group__label" htmlFor="email">Correo electrónico</label>
                  <div className="input-wrapper">
                    <svg className="input-wrapper__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" /></svg>
                    <input type="email" id="email" name="email" placeholder="nombre@correo.cl" autoComplete="email" required />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-group__label" htmlFor="password">Contraseña</label>
                  <div className="input-wrapper">
                    <svg className="input-wrapper__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></svg>
                    <input type={showPassword ? 'text' : 'password'} id="password" name="password" placeholder="········" autoComplete="current-password" required />
                    <button type="button" className="input-wrapper__icon--right" id="toggle-password" aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'} onClick={() => setShowPassword((current) => !current)}>
                      {!showPassword ? (
                        <svg className="icon-eye-open" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" /><circle cx="12" cy="12" r="3" /></svg>
                      ) : (
                        <svg className="icon-eye-closed" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" /><line x1="1" y1="1" x2="23" y2="23" /></svg>
                      )}
                    </button>
                  </div>
                </div>
                <div className="form-row">
                  <label className="checkbox-label"><input type="checkbox" name="remember" />Recordarme</label>
                  <a href="#" className="forgot-link">¿Olvidaste tu contraseña?</a>
                </div>
                <button type="submit" className="btn-primary">Ingresar</button>
              </form>
              <div className="divider" aria-hidden="true"><span className="divider__dot"></span></div>
              <p className="form-footer-text">¿Aún no tienes una cuenta?</p>
              <a href="#" className="btn-secondary">Crear cuenta</a>
            </div>
          </div>
          <div className="login-page__visual">
            <div className="animal-visual">
              <div className="mascota-circular">
                <div className="mascota-circular__bg"></div>
                <img src="/assets/img/AnimalesPrincipales.png" alt="Mascotas AmiDog – perro y gatos" className="mascota-circular__img" />
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

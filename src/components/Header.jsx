import { useContext, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { safeAuthError } from '../auth/authError'
import { AuthContext } from '../auth/authContext'
import { dashboardDestination } from '../auth/dashboardDestination'

export default function Header() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const auth = useContext(AuthContext)
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const [menuOpen, setMenuOpen] = useState(false)
  const logoutPendingRef = useRef(false)
  const dashboard = dashboardDestination(auth?.user)

  const closeMenu = () => setMenuOpen(false)

  const handleLogout = async () => {
    if (!auth || logoutPendingRef.current) {
      return
    }
    logoutPendingRef.current = true
    setLoggingOut(true)
    setLogoutError('')
    try {
      const completed = await auth.logout()
      if (completed) {
        navigate('/login', { replace: true })
      }
    } catch (requestError) {
      setLogoutError(safeAuthError(
        requestError,
        'No pudimos cerrar la sesión. Intenta nuevamente.',
      ).message)
    } finally {
      logoutPendingRef.current = false
      setLoggingOut(false)
    }
  }

  return (
    <header className="navbar">
      <div className="logo">
        <Link to="/" onClick={closeMenu}>
          <img src="/assets/img/LogoAmidog.png" alt="logo Amidog Veterinaria" className="logo-amidog" />
        </Link>
      </div>
      <button
        className="navbar__menu-button"
        type="button"
        aria-expanded={menuOpen}
        aria-controls="public-navigation"
        aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'}
        onClick={() => setMenuOpen((current) => !current)}
      >
        Menú
      </button>
      <nav
        id="public-navigation"
        aria-label="Navegación principal"
        className={menuOpen ? 'navbar__nav--open' : ''}
      >
        <ul className="links-nav">
          <li><Link to="/" onClick={closeMenu} className={pathname === '/' ? 'active' : ''}>Inicio</Link></li>
          <li><Link to="/#servicios" onClick={closeMenu}>Servicios</Link></li>
          <li><Link to="/nosotros" onClick={closeMenu} className={pathname === '/nosotros' ? 'active' : ''}>Nosotros</Link></li>
          <li><Link to="/contacto" onClick={closeMenu} className={pathname === '/contacto' ? 'active' : ''}>Contacto</Link></li>
          {dashboard && (
            <li>
              <Link
                to={dashboard.to}
                onClick={closeMenu}
                className={`btn-panel${pathname === dashboard.to ? ' active' : ''}`}
              >
                {dashboard.label}
              </Link>
            </li>
          )}
          <li className="session-control">
            {auth?.loading ? (
              <button className="btn-sesion" type="button" disabled>
                Verificando sesión…
              </button>
            ) : auth?.user ? (
              <button
                className="btn-sesion"
                type="button"
                disabled={loggingOut}
                onClick={() => {
                  closeMenu()
                  handleLogout()
                }}
              >
                {loggingOut ? 'Cerrando sesión…' : 'Cerrar sesión'}
              </button>
            ) : (
              <Link
                to="/login"
                onClick={closeMenu}
                className={`btn-sesion${pathname === '/login' ? ' active' : ''}`}
              >
                Iniciar sesión
              </Link>
            )}
            {logoutError && (
              <span className="session-error" role="alert">{logoutError}</span>
            )}
          </li>
          <li><Link to="/reservar" onClick={closeMenu} className="btn-pedir-hora">Pedir hora</Link></li>
        </ul>
      </nav>
    </header>
  )
}

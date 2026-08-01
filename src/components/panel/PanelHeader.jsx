import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { safeAuthError } from '../../auth/authError'
import { useAuth } from '../../auth/useAuth'
import logo from '../../assets/panel/logo-amidog.png'
import { ChevronDownIcon, UserIcon } from './Icons'

function PanelHeader({ onProfileAction, profileName }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const profileRef = useRef(null)
  const logoutPendingRef = useRef(false)
  const { logout, user } = useAuth()
  const navigate = useNavigate()
  const accountName = profileName?.trim() || user?.name || 'Mi cuenta'

  useEffect(() => {
    const closeProfile = (event) => {
      if (profileRef.current && !profileRef.current.contains(event.target)) setProfileOpen(false)
    }
    document.addEventListener('mousedown', closeProfile)
    return () => document.removeEventListener('mousedown', closeProfile)
  }, [])

  const closeNavigation = () => setMenuOpen(false)

  const handleLogout = async () => {
    if (logoutPendingRef.current) {
      return
    }
    logoutPendingRef.current = true
    setLoggingOut(true)
    setLogoutError('')
    try {
      const completed = await logout()
      if (completed) {
        navigate('/login', { replace: true })
        return
      }
    } catch (requestError) {
      setLogoutError(safeAuthError(
        requestError,
        'No pudimos cerrar la sesión. Intenta nuevamente.',
      ).message)
    }
    logoutPendingRef.current = false
    setLoggingOut(false)
  }

  return (
    <header className="header">
      <Link className="header__brand" to="/" aria-label="AmiDog Veterinaria" onClick={closeNavigation}>
        <img src={logo} alt="AmiDog Veterinaria" />
      </Link>

      <button
        className="menu-button"
        type="button"
        aria-label="Abrir menú"
        aria-expanded={menuOpen}
        aria-controls="panel-navigation"
        onClick={() => setMenuOpen((current) => !current)}
      >
        <span />
      </button>

      <nav id="panel-navigation" className={`header__nav ${menuOpen ? 'is-open' : ''}`} aria-label="Navegación principal">
        <Link to="/" onClick={closeNavigation}>Inicio</Link>
        <Link className="header__panel-link" to="/panel" onClick={closeNavigation}>Mi panel</Link>
        <Link to="/#servicios" onClick={closeNavigation}>Servicios</Link>
        <Link to="/nosotros" onClick={closeNavigation}>Nosotros</Link>
        <Link to="/contacto" onClick={closeNavigation}>Contacto</Link>
      </nav>

      <div className="profile" ref={profileRef}>
        <button className="profile-button" type="button" aria-expanded={profileOpen} onClick={() => setProfileOpen((current) => !current)}>
          <span className="profile-button__avatar"><UserIcon /></span>
          <span>{accountName}</span>
          <ChevronDownIcon className="profile-button__arrow" />
        </button>
        {profileOpen && (
          <div className="profile-menu">
            <button type="button" onClick={() => { setProfileOpen(false); onProfileAction() }}>Mi perfil</button>
            <button type="button" disabled={loggingOut} onClick={handleLogout}>
              {loggingOut ? 'Cerrando sesión…' : 'Cerrar sesión'}
            </button>
          </div>
        )}
        {logoutError && <p className="profile-menu__error" role="alert">{logoutError}</p>}
      </div>
    </header>
  )
}

export default PanelHeader

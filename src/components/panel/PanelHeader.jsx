import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import logo from '../../assets/panel/logo-amidog.png'
import { ChevronDownIcon, UserIcon } from './Icons'

function PanelHeader({ onProfileAction }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const profileRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    const closeProfile = (event) => {
      if (profileRef.current && !profileRef.current.contains(event.target)) setProfileOpen(false)
    }
    document.addEventListener('mousedown', closeProfile)
    return () => document.removeEventListener('mousedown', closeProfile)
  }, [])

  const closeNavigation = () => setMenuOpen(false)

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
        <Link to="/#servicios" onClick={closeNavigation}>Servicios</Link>
        <Link to="/nosotros" onClick={closeNavigation}>Nosotros</Link>
        <Link to="/contacto" onClick={closeNavigation}>Contacto</Link>
      </nav>

      <div className="profile" ref={profileRef}>
        <button className="profile-button" type="button" aria-expanded={profileOpen} onClick={() => setProfileOpen((current) => !current)}>
          <span className="profile-button__avatar"><UserIcon /></span>
          <span>Carla R.</span>
          <ChevronDownIcon className="profile-button__arrow" />
        </button>
        {profileOpen && (
          <div className="profile-menu">
            <button type="button" onClick={() => { setProfileOpen(false); onProfileAction('Tu perfil está actualizado.') }}>Mi perfil</button>
            <button type="button" onClick={() => { setProfileOpen(false); navigate('/login') }}>Cerrar sesión</button>
          </div>
        )}
      </div>
    </header>
  )
}

export default PanelHeader

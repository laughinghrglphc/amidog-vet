import { useEffect, useRef, useState } from 'react'
import profileImage from '../../../assets/admin/profile.webp'
import Icon from '../ui/Icon'

export default function Topbar({
  isMobile,
  logoutPending,
  onLogout,
  onMenuClick,
  onOpenProfile,
  onQueryChange,
  onResultSelect,
  profile,
  query,
  searchResults,
  sidebarOpen,
}) {
  const [profileOpen, setProfileOpen] = useState(false)
  const actionsRef = useRef(null)

  useEffect(() => {
    const closeMenu = (event) => {
      if (!actionsRef.current?.contains(event.target)) setProfileOpen(false)
    }

    document.addEventListener('pointerdown', closeMenu)
    return () => document.removeEventListener('pointerdown', closeMenu)
  }, [])

  return (
    <header className="admin-topbar">
      <button
        type="button"
        className="admin-topbar__menu"
        aria-controls="admin-sidebar"
        aria-expanded={isMobile ? sidebarOpen : undefined}
        aria-label={isMobile ? (sidebarOpen ? 'Cerrar menú' : 'Abrir menú') : 'Contraer menú'}
        onClick={onMenuClick}
      >
        <Icon name="menu" />
      </button>

      <div className="admin-search-wrap">
        <label className="admin-search">
          <span className="sr-only">Buscar en el panel</span>
          <input
            type="search"
            aria-label="Buscar en el panel"
            autoComplete="off"
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="Buscar mascotas, clientes o reservas..."
          />
          {query ? (
            <button type="button" aria-label="Limpiar búsqueda" onClick={() => onQueryChange('')}>
              <Icon name="close" size={16} />
            </button>
          ) : (
            <Icon name="search" size={19} />
          )}
        </label>

        {query.trim().length >= 2 && (
          <div
            className="admin-search-results"
            aria-label="Resultados de búsqueda"
            aria-live="polite"
            role="region"
          >
            {searchResults.length ? searchResults.map((result) => (
              <button
                type="button"
                key={`${result.kind}-${result.id}`}
                aria-label={`${result.title}. ${result.detail}`}
                onClick={() => onResultSelect(result)}
              >
                <span>{result.eyebrow}</span>
                <strong>{result.title}</strong>
                <small>{result.detail}</small>
              </button>
            )) : (
              <p>No encontramos resultados para “{query}”.</p>
            )}
          </div>
        )}
      </div>

      <div className="admin-topbar__actions" ref={actionsRef}>
        <div className="admin-profile-wrap">
          <button
            type="button"
            className="admin-profile"
            aria-controls="profile-menu"
            aria-expanded={profileOpen}
            aria-label="Abrir menú de usuario"
            onClick={() => setProfileOpen((current) => !current)}
          >
            <img src={profileImage} alt="" />
            <span><strong>{profile.name}</strong><small>Administradora</small></span>
            <Icon name="chevronDown" size={16} />
          </button>

          {profileOpen && (
            <div id="profile-menu" className="admin-dropdown admin-profile-dropdown">
              <button
                type="button"
                onClick={() => {
                  setProfileOpen(false)
                  onOpenProfile()
                }}
              >
                <Icon name="user" size={18} /><span>Mi perfil</span>
              </button>
              <button
                type="button"
                className="is-danger"
                disabled={logoutPending}
                onClick={() => {
                  setProfileOpen(false)
                  onLogout()
                }}
              >
                <Icon name="logout" size={18} />
                <span>{logoutPending ? 'Cerrando sesión…' : 'Cerrar sesión'}</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}

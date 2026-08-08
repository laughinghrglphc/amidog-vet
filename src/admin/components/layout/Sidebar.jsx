import careDog from '../../../assets/admin/golden.webp'
import logo from '../../../assets/admin/logo-amidog.webp'
import Icon from '../ui/Icon'

export default function Sidebar({
  activeItem,
  closeButtonRef,
  isMobile,
  items,
  onClose,
  onNavigate,
  open,
  unreadCount = 0,
}) {
  const hidden = isMobile && !open

  return (
    <aside
      id="admin-sidebar"
      aria-hidden={hidden}
      aria-label="Navegación principal"
      className={`admin-sidebar ${open ? 'is-open' : ''}`}
      inert={hidden ? true : undefined}
    >
      <div className="admin-sidebar__top">
        <img src={logo} alt="AmiDog Veterinaria" className="admin-sidebar__logo" />
        <button
          ref={closeButtonRef}
          type="button"
          className="admin-sidebar__close"
          aria-label="Cerrar menú"
          onClick={onClose}
        >
          <Icon name="close" />
        </button>
      </div>

      <nav className="admin-sidebar__nav" aria-label="Secciones del panel">
        {items.map((item) => {
          const hasUnread = item.modal === 'notifications' && unreadCount > 0
          return (
            <button
              type="button"
              key={item.label}
              aria-label={hasUnread
                ? `${item.label}, ${unreadCount} sin leer`
                : item.label}
              className={activeItem === item.label ? 'is-active' : ''}
              onClick={() => onNavigate(item)}
            >
              <Icon name={item.icon} size={19} />
              <span>{item.label}</span>
              {hasUnread && (
                <span className="admin-sidebar__badge" aria-hidden="true">
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </button>
          )
        })}
      </nav>

      <div className="admin-sidebar__care" aria-hidden="true">
        <img src={careDog} alt="" />
        <span className="admin-sidebar__sparkle admin-sidebar__sparkle--left">✦</span>
        <span className="admin-sidebar__sparkle admin-sidebar__sparkle--right">✦</span>
        <h3>Cuidamos a quienes<br />te acompañan</h3>
      </div>
    </aside>
  )
}

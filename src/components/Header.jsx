import { Link, useLocation } from 'react-router-dom'

export default function Header() {
  const { pathname } = useLocation()

  return (
    <header className="navbar">
      <div className="logo">
        <Link to="/">
          <img src="/assets/img/LogoAmidog.png" alt="logo Amidog Veterinaria" className="logo-amidog" />
        </Link>
      </div>
      <nav>
        <ul className="links-nav">
          <li><Link to="/" className={pathname === '/' ? 'active' : ''}>Inicio</Link></li>
          <li><Link to="/#servicios">Servicios</Link></li>
          <li><Link to="/nosotros" className={pathname === '/nosotros' ? 'active' : ''}>Nosotros</Link></li>
          <li><Link to="/contacto" className={pathname === '/contacto' ? 'active' : ''}>Contacto</Link></li>
          <li><Link to="/reservar" className="btn-pedir-hora">Pedir hora</Link></li>
        </ul>
      </nav>
    </header>
  )
}

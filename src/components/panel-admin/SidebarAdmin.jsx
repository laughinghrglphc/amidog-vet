import React from 'react';
import logoAmidog from '/assets/img/logo-amidog.png';
import { Icon } from './Icon.jsx';

const navItems = [
  { label: 'Panel', icon: 'home', active: true },
  { label: 'Reservas', icon: 'calendar' },
  { label: 'Clientes', icon: 'users' },
  { label: 'Mascotas', icon: 'paw' },
  { label: 'Servicios', icon: 'stethoscope' },
  { label: 'Calendario', icon: 'calendar' },
  { label: 'Configuración', icon: 'settings' },
];

export const SidebarAdmin = () => (
  <aside className="sidebar">
    <div className="brand-logo">
      <img src={logoAmidog} alt="Logo Amidog Veterinaria" />
    </div>
    <div className="nav-menu">
      {navItems.map((item, index) => (
        <a
          key={index}
          className={`nav-item ${item.active ? 'active' : ''}`}
          href="#"
          onClick={(e) => e.preventDefault()}
        >
          <Icon name={item.icon} size={18} />
          {item.label}
        </a>
      ))}
    </div>
  </aside>
);

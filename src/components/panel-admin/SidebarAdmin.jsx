import React from 'react';
// Importamos el logo desde la carpeta assets
import logoAmidog from '../../assets/panel/logo-amidog.png';
import { Icon } from './Icon.jsx'; 

// ARREGLADO: Ahora usamos 'label' e 'icon' para que el HTML de abajo los encuentre
const navItems = [
  { label: 'Panel', icon: 'home', active: true },
  { label: 'Reservas', icon: 'calendar', active: false },
  { label: 'Clientes', icon: 'users', active: false },
  { label: 'Mascotas', icon: 'paw', active: false },
  { label: 'Servicios', icon: 'stethoscope', active: false },
  { label: 'Calendario', icon: 'calendar', active: false },
  { label: 'Configuración', icon: 'settings', active: false },
];

export const SidebarAdmin = () => (
  <aside className="sidebar">
    <div className="brand-logo">
      <img src={logoAmidog} alt="Logo Amidog" />
    </div>
    
    <div className="nav-menu">
      {navItems.map((item, index) => (
        <a key={index} className={`nav-item ${item.active ? 'active' : ''}`}>
          <span style={{ marginRight: '10px', display: 'flex', alignItems: 'center' }}>
            <Icon name={item.icon} size={16} />
          </span>
          {item.label}
        </a>
      ))}
    </div>
  </aside>
);
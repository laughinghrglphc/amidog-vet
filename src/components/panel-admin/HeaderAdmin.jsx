import React from 'react';
import { Icon } from './Icon.jsx';

export const HeaderAdmin = () => {
  return (
    <header className="header">
      <div className="header-left">
        <button className="menu-btn" type="button" aria-label="Abrir menú">
          <Icon name="menu" size={22} />
        </button>
        <div className="search-wrapper">
          <input
            type="text"
            className="search-bar"
            placeholder="Buscar clientes, mascotas, reservas..."
          />
          <span className="search-icon">
            <Icon name="search" size={18} />
          </span>
        </div>
      </div>

      <div className="header-right">
        <div className="notification-badge">
          <Icon name="bell" size={22} />
          <span className="badge-count">5</span>
        </div>

        <div className="user-profile">
          <div className="user-info">
            <div className="user-name">Nataly Apablaza</div>
            <div className="user-role">Administradora</div>
          </div>
          <div className="user-avatar">
            <span role="img" aria-label="Avatar de administradora">👩🏽‍⚕️</span>
          </div>
        </div>
      </div>
    </header>
  );
};

export default HeaderAdmin;

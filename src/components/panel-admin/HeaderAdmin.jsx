import React from 'react';
import { Icon } from './Icon.jsx'; // Ajusta la ruta si está en otra carpeta

export const HeaderAdmin = () => {
  return (
    <header className="header" style={{ 
      display: 'flex', 
      justifyContent: 'space-between', 
      alignItems: 'center', 
      width: '100%',
      padding: '16px 24px',
      backgroundColor: '#ffffff',
      borderBottom: '1px solid #F3F4F6'
    }}>
      
      {/* LADO IZQUIERDO: Menú Hamburguesa + Buscador con Lupa */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: 1, maxWidth: '520px' }}>
        {/* Botón Menú Hamburguesa */}
        <button 
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            padding: '4px',
            display: 'flex',
            alignItems: 'center',
            color: '#374151'
          }}
        >
          <Icon name="menu" size={22} />
        </button>

        {/* Buscador */}
        <div style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          width: '100%'
        }}>
          <input 
            type="text" 
            className="search-bar" 
            placeholder="Buscar clientes, mascotas, reservas..." 
            style={{
              width: '100%',
              padding: '10px 40px 10px 16px',
              borderRadius: '12px',
              border: '1px solid #E5E7EB',
              fontSize: '14px',
              outline: 'none',
              color: '#374151',
              backgroundColor: '#FFFFFF'
            }}
          />
          {/* Lupa dentro del input */}
          <span style={{
            position: 'absolute',
            right: '14px',
            display: 'flex',
            alignItems: 'center',
            pointerEvents: 'none',
            color: '#6B7280'
          }}>
            <Icon name="search" size={18} />
          </span>
        </div>
      </div>

      {/* LADO DERECHO: Notificaciones + Perfil de Nataly */}
      <div className="header-right" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
        
        {/* Notificaciones */}
        <div className="notification-badge" style={{ position: 'relative', cursor: 'pointer', display: 'flex', alignItems: 'center' }}>
          <Icon name="bell" size={22} />
          <span className="badge-count" style={{
            position: 'absolute',
            top: '-6px',
            right: '-6px',
            backgroundColor: '#FF7100',
            color: '#fff',
            borderRadius: '50%',
            width: '18px',
            height: '18px',
            fontSize: '11px',
            fontWeight: 'bold',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            5
          </span>
        </div>

        {/* Perfil de Nataly */}
        <div className="user-profile" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontWeight: 'bold', fontSize: '14px', color: '#333' }}>Nataly Apablaza</div>
            <div style={{ fontSize: '12px', color: '#888' }}>Administradora</div>
          </div>
          
          {/* Círculo de Avatar */}
          <div style={{ 
            width: '40px', 
            height: '40px', 
            borderRadius: '50%', 
            backgroundColor: '#fbe2c5', 
            display: 'flex', 
            justifyContent: 'center', 
            alignItems: 'center', 
            fontSize: '20px' 
          }}>
            👩🏽‍⚕️
          </div>
        </div>

      </div>

    </header>
  );
};

export default HeaderAdmin;
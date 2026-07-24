import React, { useState } from 'react';
import { Icon } from './Icon.jsx';

export const AppointmentsTable = ({ appointments, onOpenAppointment, onToggleStatus, onDelete }) => {
  const [rowMenu, setRowMenu] = useState(null);

  return (
    <article className="admin-card admin-appointments">
      <header className="admin-card__header">
        <h3><Icon name="calendar" size={17} /> Próximas reservas</h3>
        <button type="button">Ver todas</button>
      </header>
      
      <div className="admin-appointments__table">
        <div className="admin-appointments__row admin-appointments__row--head">
          <span>Mascota</span>
          <span>Dueño</span>
          <span>Servicio</span>
          <span>Fecha y hora</span>
          <span>Estado</span>
          <span />
        </div>
        
        {appointments.map((appointment, index) => (
          <div className="admin-appointments__row" key={appointment.id}>
            <span className="admin-pet-cell" data-label="Mascota">
              <img src={appointment.image} alt={appointment.pet} />
              <span><strong>{appointment.pet}</strong><small>{appointment.type}</small></span>
            </span>
            <span data-label="Dueño">{appointment.owner}</span>
            <span className="admin-service-cell" data-label="Servicio">
              <i><Icon name="stethoscope" size={12} /></i>{appointment.service}
            </span>
            <span data-label="Fecha y hora">{appointment.time}</span>
            <span data-label="Estado">
              <em className={appointment.status === 'Confirmada' ? 'is-confirmed' : 'is-pending'}>
                {appointment.status}
              </em>
            </span>
            <div className="admin-row-actions">
              <button 
                type="button" 
                aria-label={`Opciones de ${appointment.pet}`} 
                onClick={(event) => { 
                  event.stopPropagation(); 
                  setRowMenu(rowMenu === appointment.id ? null : appointment.id);
                }}
              >
                <Icon name="more" size={16} />
              </button>
              
              {rowMenu === appointment.id && (
                <div className={`admin-row-menu ${index >= appointments.length - 3 ? 'admin-row-menu--up' : ''}`}>
                  <button type="button" onClick={() => onOpenAppointment(appointment)}>
                    <Icon name="info" size={15} /> Ver detalle
                  </button>
                  <button type="button" onClick={() => onToggleStatus(appointment)}>
                    <Icon name="check" size={15} /> Cambiar estado
                  </button>
                  <button type="button" className="is-danger" onClick={() => onDelete(appointment)}>
                    <Icon name="trash" size={15} /> Eliminar
                  </button>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </article>
  );
};
import React from 'react';
import { Icon } from './Icon.jsx';
import dogImg from '../../assets/panel/max.png';
import catImg from '../../assets/panel/annie.png';

const petImages = {
  Luna: dogImg,
  Milo: catImg,
  Rocky: dogImg,
  Nala: catImg,
  Max: dogImg,
};

const mockBookings = [
  { pet: 'Luna', type: 'Perro', owner: 'María González', service: 'Consulta general', time: 'Hoy, 11:00', status: 'Confirmada' },
  { pet: 'Milo', type: 'Gato', owner: 'Carlos Ramírez', service: 'Vacunas', time: 'Hoy, 15:30', status: 'Pendiente' },
  { pet: 'Rocky', type: 'Perro', owner: 'Ana Torres', service: 'Peluquería', time: 'Mañana, 09:00', status: 'Confirmada' },
  { pet: 'Nala', type: 'Gato', owner: 'Jorge Valdés', service: 'Consulta general', time: 'Mañana, 11:30', status: 'Pendiente' },
  { pet: 'Max', type: 'Perro', owner: 'Lucía Fernández', service: 'Vacunas', time: 'May, 17, 16:00', status: 'Confirmada' },
];

export const BookingsTableAdmin = () => (
  <div className="card">
    <div className="card-header">
      <div className="card-header-left">
        <Icon name="calendar" size={18} />
        <h3 className="card-title">Próximas reservas</h3>
      </div>
      <a className="link-action" href="#" onClick={(e) => e.preventDefault()}>Ver todas</a>
    </div>
    <table className="table-container">
      <thead>
        <tr>
          <th>Mascota</th>
          <th>Dueño</th>
          <th>Servicio</th>
          <th>Fecha y hora</th>
          <th>Estado</th>
        </tr>
      </thead>
      <tbody>
        {mockBookings.map((b, i) => (
          <tr key={i}>
            <td>
              <div className="pet-cell">
                <img className="pet-avatar" src={petImages[b.pet]} alt={b.pet} />
                <div className="pet-info">
                  <span className="pet-name">{b.pet}</span>
                  <span className="pet-type">{b.type}</span>
                </div>
              </div>
            </td>
            <td>{b.owner}</td>
            <td>{b.service}</td>
            <td>{b.time}</td>
            <td>
              <span className={`status-badge ${b.status.toLowerCase()}`}>
                {b.status}
              </span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

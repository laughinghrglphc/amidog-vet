import React from 'react';

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
      <h3 className="card-title">Próximas reservas</h3>
      <a className="link-action">Ver todas</a>
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
              <div className="pet-name">{b.pet}</div>
              <div className="pet-type">{b.type}</div>
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
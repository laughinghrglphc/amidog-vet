

export const summaryCards = [
  { label: 'Reservas de hoy', value: '12', detail: '20% vs. ayer', icon: 'calendar', tone: 'orange', modal: 'reservas' },
  { label: 'Clientes registrados', value: '248', detail: '15% vs. semana pasada', icon: 'users', tone: 'teal', modal: 'clientes' },
  { label: 'Mascotas registradas', value: '376', detail: '18% vs. semana pasada', icon: 'paw', tone: 'yellow', modal: 'mascotas' },
];

export const appointments = [
  { id: 1, pet: 'Max', type: 'Perro', owner: 'Juan Pérez', service: 'Consulta general', time: 'Hoy, 10:00', status: 'Confirmada', image: 'https://via.placeholder.com/40' },
  { id: 2, pet: 'Luna', type: 'Gato', owner: 'María López', service: 'Vacunas', time: 'Hoy, 11:30', status: 'Pendiente', image: 'https://via.placeholder.com/40' },
  { id: 3, pet: 'Rocky', type: 'Perro', owner: 'Carlos Díaz', service: 'Peluquería', time: 'Hoy, 15:00', status: 'Confirmada', image: 'https://via.placeholder.com/40' },
];


export const schedule = [
  { time: '10:00', title: 'Consulta general', pet: 'Max', tone: 'orange' },
  { time: '11:30', title: 'Vacunas', pet: 'Luna', tone: 'teal' },
  { time: '15:00', title: 'Peluquería', pet: 'Rocky', tone: 'yellow' },
];

export const chartData = {
  'Esta semana': [12, 19, 15, 25, 22, 30, 28],
  'Semana pasada': [10, 15, 12, 20, 18, 25, 22],
  'Este mes': [50, 65, 55, 80, 70, 90, 85]
};
import React, { useState } from 'react';
import { SidebarAdmin } from '../components/panel-admin/SidebarAdmin.jsx';
import { HeaderAdmin } from '../components/panel-admin/HeaderAdmin.jsx';
import { KpiCardAdmin } from '../components/panel-admin/KpiCardAdmin.jsx';
import { BookingsTableAdmin } from '../components/panel-admin/BookingsTableAdmin.jsx';
import { Icon } from '../components/panel-admin/Icon.jsx';
import petsHero from '/assets/img/pets-hero.png';

const chartDatasets = {
  'Esta semana': {
    points: '30,110 88,80 146,116 204,65 262,38 320,86 378,122',
    polygon: '30,110 88,80 146,116 204,65 262,38 320,86 378,122 378,140 30,140',
    dots: [{ x: 30, y: 110 }, { x: 88, y: 80 }, { x: 146, y: 116 }, { x: 204, y: 65 }, { x: 262, y: 38 }, { x: 320, y: 86 }, { x: 378, y: 122 }],
    labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
    report: { total: '102', avg: '14', topDay: 'Viernes' },
  },
  'Semana pasada': {
    points: '30,120 88,95 146,90 204,75 262,50 320,60 378,100',
    polygon: '30,120 88,95 146,90 204,75 262,50 320,60 378,100 378,140 30,140',
    dots: [{ x: 30, y: 120 }, { x: 88, y: 95 }, { x: 146, y: 90 }, { x: 204, y: 75 }, { x: 262, y: 50 }, { x: 320, y: 60 }, { x: 378, y: 100 }],
    labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
    report: { total: '95', avg: '13', topDay: 'Viernes' },
  },
  'Este mes': {
    points: '30,130 88,100 146,70 204,85 262,50 320,40 378,90',
    polygon: '30,130 88,100 146,70 204,85 262,50 320,40 378,90 378,140 30,140',
    dots: [{ x: 30, y: 130 }, { x: 88, y: 100 }, { x: 146, y: 70 }, { x: 204, y: 85 }, { x: 262, y: 50 }, { x: 320, y: 40 }, { x: 378, y: 90 }],
    labels: ['Sem 1', 'Sem 2', 'Sem 3', 'Sem 4', '', '', ''],
    report: { total: '144', avg: '21', topDay: 'Sábado' },
  },
  'Este año': {
    points: '30,140 88,110 146,80 204,60 262,45 320,30 378,20',
    polygon: '30,140 88,110 146,80 204,60 262,45 320,30 378,20 378,140 30,140',
    dots: [{ x: 30, y: 140 }, { x: 88, y: 110 }, { x: 146, y: 80 }, { x: 204, y: 60 }, { x: 262, y: 45 }, { x: 320, y: 30 }, { x: 378, y: 20 }],
    labels: ['Ene', 'Mar', 'May', 'Jul', 'Sep', 'Nov', 'Dic'],
    report: { total: '1.280', avg: '106', topDay: 'Sábado' },
  },
};

const agendaEvents = [
  { time: '09:00', title: 'Consulta general', pet: 'Rocky', color: '#08a89e' },
  { time: '11:00', title: 'Consulta general', pet: 'Luna', color: '#08a89e' },
  { time: '15:30', title: 'Vacunas', pet: 'Milo', color: '#08a89e' },
  { time: '17:00', title: 'Peluquería', pet: 'Coco', color: '#ff7100' },
];

const dropdownOptions = ['Esta semana', 'Semana pasada', 'Este mes', 'Este año'];

export const PanelAdmin = () => {
  const [filterPeriod, setFilterPeriod] = useState('Esta semana');
  const [showFilterDropdown, setShowFilterDropdown] = useState(false);
  const [activeModal, setActiveModal] = useState(null);

  const today = new Date();
  const welcomeOptions = { weekday: 'long', day: 'numeric', month: 'long' };
  const formattedWelcome = today.toLocaleDateString('es-ES', welcomeOptions);
  const welcomeDate = formattedWelcome.charAt(0).toUpperCase() + formattedWelcome.slice(1);

  const [viewDate, setViewDate] = useState(new Date());
  const handlePrevMonth = () => setViewDate((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1));
  const handleNextMonth = () => setViewDate((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1));

  const viewMonth = viewDate.getMonth();
  const viewYear = viewDate.getFullYear();
  const monthName = viewDate.toLocaleDateString('es-ES', { month: 'long' });
  const calendarTitle = monthName.charAt(0).toUpperCase() + monthName.slice(1) + ' ' + viewYear;

  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  let firstDayIndex = new Date(viewYear, viewMonth, 1).getDay();
  firstDayIndex = firstDayIndex === 0 ? 6 : firstDayIndex - 1;

  const isCurrentMonth = today.getMonth() === viewMonth && today.getFullYear() === viewYear;
  const currentDay = today.getDate();

  const currentChart = chartDatasets[filterPeriod] || chartDatasets['Esta semana'];

  return (
    <div className="panel-admin-container">
      <SidebarAdmin />
      <div className="main-wrapper">
        <HeaderAdmin />
        <main className="content">

          {/* Bienvenida */}
          <div className="welcome-section">
            <div className="welcome-text">
              <h2>Bienvenida, Nataly</h2>
              <p>Aquí tienes un resumen claro de lo que sucede hoy en AmiDog.</p>
            </div>
            <div className="date-pill">
              <span className="dot"></span> {welcomeDate}
            </div>
          </div>

          {/* KPIs */}
          <section className="kpi-grid">
            <KpiCardAdmin
              title="Reservas de hoy"
              value="12"
              subtext="20% vs. ayer"
              colorClass="teal"
              icon={<Icon name="calendar" size={22} />}
              imgSrc="/assets/img/LogoCalendario.png"
            />
            <KpiCardAdmin
              title="Clientes registrados"
              value="248"
              subtext="15% vs. semana pasada"
              colorClass="orange-light"
              icon={<Icon name="users" size={22} />}
              imgSrc="/assets/img/LogoUsuarios.png"
            />
            <KpiCardAdmin
              title="Mascotas registradas"
              value="376"
              subtext="18% vs. semana pasada"
              colorClass="orange-dark"
              icon={<Icon name="paw" size={22} />}
              imgSrc="/assets/img/patitas.png"
            />
            <div className="kpi-hero-image">
              <img src={petsHero} alt="Mascotas AmiDog" />
            </div>
          </section>

          {/* GRÁFICO + TABLA */}
          <section className="dashboard-sections">

            {/* Reservas semanales */}
            <div className="card">
              <div className="card-header">
                <div className="card-header-left">
                  <Icon name="calendar" size={18} />
                  <h3 className="card-title">Reservas semanales</h3>
                </div>
                <div style={{ position: 'relative' }}>
                  <button
                    className="filter-btn"
                    onClick={() => setShowFilterDropdown(!showFilterDropdown)}
                  >
                    {filterPeriod} <span style={{ fontSize: '10px', color: '#9ca3af' }}>▾</span>
                  </button>
                  {showFilterDropdown && (
                    <div className="filter-dropdown">
                      {dropdownOptions.map((opt) => (
                        <div
                          key={opt}
                          className={`filter-option ${filterPeriod === opt ? 'is-active' : ''}`}
                          onClick={() => {
                            setFilterPeriod(opt);
                            setShowFilterDropdown(false);
                          }}
                        >
                          {opt}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* SVG Chart */}
              <div style={{ width: '100%', height: '180px', marginTop: '8px' }}>
                <svg viewBox="0 0 400 160" style={{ width: '100%', height: '100%', overflow: 'visible' }}>
                  <defs>
                    <linearGradient id="orangeGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#FF7100" stopOpacity="0.3" />
                      <stop offset="100%" stopColor="#FF7100" stopOpacity="0.0" />
                    </linearGradient>
                  </defs>

                  {/* Grid lines */}
                  <line x1="30" y1="20" x2="380" y2="20" stroke="#F3F4F6" strokeDasharray="4" />
                  <text x="15" y="23" fontSize="10" fill="#9CA3AF">25</text>
                  <line x1="30" y1="50" x2="380" y2="50" stroke="#F3F4F6" strokeDasharray="4" />
                  <text x="15" y="53" fontSize="10" fill="#9CA3AF">20</text>
                  <line x1="30" y1="80" x2="380" y2="80" stroke="#F3F4F6" strokeDasharray="4" />
                  <text x="15" y="83" fontSize="10" fill="#9CA3AF">15</text>
                  <line x1="30" y1="110" x2="380" y2="110" stroke="#F3F4F6" strokeDasharray="4" />
                  <text x="15" y="113" fontSize="10" fill="#9CA3AF">10</text>
                  <line x1="30" y1="140" x2="380" y2="140" stroke="#F3F4F6" strokeDasharray="4" />
                  <text x="15" y="143" fontSize="10" fill="#9CA3AF">5</text>

                  {/* Gradient fill */}
                  <polygon points={currentChart.polygon} fill="url(#orangeGradient)" />
                  {/* Line */}
                  <polyline fill="none" stroke="#FF7100" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" points={currentChart.points} />
                  {/* Dots */}
                  {currentChart.dots.map((pt, idx) => (
                    <circle key={idx} cx={pt.x} cy={pt.y} r="4" fill="#FFFFFF" stroke="#FF7100" strokeWidth="2" />
                  ))}
                  {/* Labels */}
                  {currentChart.labels.map((label, idx) => (
                    <text key={idx} x={30 + idx * 58} y="158" textAnchor="middle" fontSize="11" fill="#6B7280">{label}</text>
                  ))}
                </svg>
              </div>

              <div className="chart-link">
                <button onClick={() => setActiveModal('report')}>Ver reporte completo &gt;</button>
              </div>
            </div>

            {/* Tabla de reservas */}
            <BookingsTableAdmin />
          </section>

          {/* FILA INFERIOR */}
          <section className="dashboard-bottom-grid">

            {/* Servicios más solicitados */}
            <div className="card services-card">
              <div className="card-header-bottom">
                <h3 className="card-title">Servicios más solicitados</h3>
              </div>
              <div className="donut-section">
                <div className="donut-chart">
                  <svg viewBox="0 0 36 36">
                    <circle cx="18" cy="18" r="15.915" fill="transparent" stroke="#FF7100" strokeWidth="4.5" strokeDasharray="40 60" strokeDashoffset="0" />
                    <circle cx="18" cy="18" r="15.915" fill="transparent" stroke="#08A89E" strokeWidth="4.5" strokeDasharray="25 75" strokeDashoffset="-40" />
                    <circle cx="18" cy="18" r="15.915" fill="transparent" stroke="#F4A261" strokeWidth="4.5" strokeDasharray="20 80" strokeDashoffset="-65" />
                    <circle cx="18" cy="18" r="15.915" fill="transparent" stroke="#B0BEC5" strokeWidth="4.5" strokeDasharray="15 85" strokeDashoffset="-85" />
                  </svg>
                  <div className="donut-center">
                    <span className="donut-label">Total</span>
                    <span className="donut-total">268</span>
                  </div>
                </div>
                <div className="donut-legend">
                  <div className="legend-item">
                    <span className="legend-dot" style={{ backgroundColor: '#FF7100' }}></span>
                    <span className="legend-label"><strong>Consulta general</strong> 40% (107)</span>
                  </div>
                  <div className="legend-item">
                    <span className="legend-dot" style={{ backgroundColor: '#08A89E' }}></span>
                    <span className="legend-label"><strong>Vacunas</strong> 25% (67)</span>
                  </div>
                  <div className="legend-item">
                    <span className="legend-dot" style={{ backgroundColor: '#F4A261' }}></span>
                    <span className="legend-label"><strong>Peluquería</strong> 20% (54)</span>
                  </div>
                  <div className="legend-item">
                    <span className="legend-dot" style={{ backgroundColor: '#B0BEC5' }}></span>
                    <span className="legend-label"><strong>Otros</strong> 15% (40)</span>
                  </div>
                </div>
              </div>
              <div className="services-footer">
                <a className="link-action" href="#" onClick={(e) => e.preventDefault()}>Ver todos los servicios &gt;</a>
              </div>
            </div>

            {/* Agenda de hoy */}
            <div className="card agenda-card">
              <div className="card-header-bottom">
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Icon name="calendar" size={18} />
                  <h3 className="card-title">Agenda de hoy</h3>
                </div>
              </div>
              <div className="agenda-timeline">
                {agendaEvents.map((item, idx) => (
                  <div className="agenda-item" key={idx}>
                    <span className="agenda-time" style={{ color: item.color }}>{item.time}</span>
                    <span className="agenda-dot" style={{ backgroundColor: item.color }}></span>
                    <div className="agenda-info">
                      <div className="agenda-title">{item.title}</div>
                      <div className="agenda-pet">{item.pet}</div>
                    </div>
                  </div>
                ))}
              </div>
              <div className="services-footer">
                <a className="link-action" href="#" onClick={(e) => e.preventDefault()}>Ver calendario &gt;</a>
              </div>
            </div>

            {/* Foto gato */}
            <div className="card photo-card">
              <img src="/assets/img/GatitoAsomado.png" alt="Gato de AmiDog" />
            </div>

            {/* Calendario */}
            <div className="card calendar-card">
              <div className="calendar-nav">
                <button className="calendar-nav-btn" onClick={handlePrevMonth} aria-label="Mes anterior">
                  <Icon name="chevronLeft" size={18} />
                </button>
                <div className="calendar-title">
                  <Icon name="calendar" size={16} />
                  {calendarTitle}
                </div>
                <button className="calendar-nav-btn" onClick={handleNextMonth} aria-label="Mes siguiente">
                  <Icon name="chevronRight" size={18} />
                </button>
              </div>
              <div className="calendar-grid">
                <div className="calendar-weekdays">
                  <span>Lu</span><span>Ma</span><span>Mi</span><span>Ju</span><span>Vi</span><span>Sá</span><span>Do</span>
                </div>
                <div className="calendar-days">
                  {Array.from({ length: firstDayIndex }, (_, i) => (
                    <div className="calendar-day is-empty" key={`prev-${i}`}>
                      <span className="day-number">-</span>
                    </div>
                  ))}
                  {Array.from({ length: daysInMonth }, (_, i) => {
                    const day = i + 1;
                    const isToday = isCurrentMonth && day === currentDay;
                    const isPast = isCurrentMonth && day < currentDay;
                    return (
                      <div className={`calendar-day ${isToday ? 'is-today' : ''} ${isPast ? 'is-past' : ''}`} key={day}>
                        <span className="day-number">{day}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </section>

          {/* MODAL: REPORTE */}
          {activeModal === 'report' && (
            <div className="admin-modal-backdrop" onClick={() => setActiveModal(null)}>
              <div className="admin-modal" onClick={(e) => e.stopPropagation()}>
                <div className="admin-modal-header">
                  <h2>Reporte · {filterPeriod}</h2>
                  <button className="close-btn" onClick={() => setActiveModal(null)}>
                    <Icon name="close" size={20} />
                  </button>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px' }}>
                  <div style={{ backgroundColor: '#F0FBF9', borderRadius: '16px', padding: '30px 16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '38px', fontWeight: 'bold', color: '#00A896', marginBottom: '8px', lineHeight: '1' }}>
                      {currentChart.report.total}
                    </div>
                    <div style={{ fontSize: '12px', color: '#6B7280' }}>reservas en el período</div>
                  </div>
                  <div style={{ backgroundColor: '#F0FBF9', borderRadius: '16px', padding: '30px 16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '38px', fontWeight: 'bold', color: '#00A896', marginBottom: '8px', lineHeight: '1' }}>
                      {currentChart.report.avg}
                    </div>
                    <div style={{ fontSize: '12px', color: '#6B7280' }}>reservas promedio por día</div>
                  </div>
                  <div style={{ backgroundColor: '#F0FBF9', borderRadius: '16px', padding: '30px 16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '32px', fontWeight: 'bold', color: '#00A896', marginBottom: '8px', lineHeight: '1.1' }}>
                      {currentChart.report.topDay}
                    </div>
                    <div style={{ fontSize: '12px', color: '#6B7280' }}>día con más reservas</div>
                  </div>
                </div>
              </div>
            </div>
          )}

        </main>
      </div>
    </div>
  );
};

export default PanelAdmin;

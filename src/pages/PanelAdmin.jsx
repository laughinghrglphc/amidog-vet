import React, { useState } from 'react';
import { SidebarAdmin } from '../components/panel-admin/SidebarAdmin.jsx';
import { HeaderAdmin } from '../components/panel-admin/HeaderAdmin.jsx';
import { KpiCardAdmin } from '../components/panel-admin/KpiCardAdmin.jsx';
import { BookingsTableAdmin } from '../components/panel-admin/BookingsTableAdmin.jsx';
import { Icon } from '../components/panel-admin/Icon.jsx'; 

export const PanelAdmin = () => {
  // --- ESTADOS INTERACTIVOS ---
  const [filterPeriod, setFilterPeriod] = useState('Esta semana');
  const [showFilterDropdown, setShowFilterDropdown] = useState(false);
  const [activeModal, setActiveModal] = useState(null); // 'services' | 'report' | null
  const [selectedAppointment, setSelectedAppointment] = useState(null);

  // DATASETS DEL GRÁFICO Y REPORTE SEGÚN EL PERÍODO
  const chartDatasets = {
    'Esta semana': {
      points: "30,110 88,80 146,116 204,65 262,38 320,86 378,122",
      polygon: "30,110 88,80 146,116 204,65 262,38 320,86 378,122 378,140 30,140",
      dots: [{ x: 30, y: 110 }, { x: 88, y: 80 }, { x: 146, y: 116 }, { x: 204, y: 65 }, { x: 262, y: 38 }, { x: 320, y: 86 }, { x: 378, y: 122 }],
      labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
      report: { total: '102', avg: '14', topDay: 'Viernes' }
    },
    'Semana pasada': {
      points: "30,120 88,95 146,90 204,75 262,50 320,60 378,100",
      polygon: "30,120 88,95 146,90 204,75 262,50 320,60 378,100 378,140 30,140",
      dots: [{ x: 30, y: 120 }, { x: 88, y: 95 }, { x: 146, y: 90 }, { x: 204, y: 75 }, { x: 262, y: 50 }, { x: 320, y: 60 }, { x: 378, y: 100 }],
      labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
      report: { total: '95', avg: '13', topDay: 'Viernes' }
    },
    'Este mes': {
      points: "30,130 88,100 146,70 204,85 262,50 320,40 378,90",
      polygon: "30,130 88,100 146,70 204,85 262,50 320,40 378,90 378,140 30,140",
      dots: [{ x: 30, y: 130 }, { x: 88, y: 100 }, { x: 146, y: 70 }, { x: 204, y: 85 }, { x: 262, y: 50 }, { x: 320, y: 40 }, { x: 378, y: 90 }],
      labels: ['Sem 1', 'Sem 2', 'Sem 3', 'Sem 4', '', '', ''],
      report: { total: '144', avg: '21', topDay: 'Sábado' }
    },
    'Este año': {
      points: "30,140 88,110 146,80 204,60 262,45 320,30 378,20",
      polygon: "30,140 88,110 146,80 204,60 262,45 320,30 378,20 378,140 30,140",
      dots: [{ x: 30, y: 140 }, { x: 88, y: 110 }, { x: 146, y: 80 }, { x: 204, y: 60 }, { x: 262, y: 45 }, { x: 320, y: 30 }, { x: 378, y: 20 }],
      labels: ['Ene', 'Mar', 'May', 'Jul', 'Sep', 'Nov', 'Dic'],
      report: { total: '1.280', avg: '106', topDay: 'Sábado' }
    }
  };

  const currentChart = chartDatasets[filterPeriod] || chartDatasets['Esta semana'];

  // AGENDA DE HOY
  const agendaEvents = [
    { time: '09:00', title: 'Consulta general', pet: 'Rocky', owner: 'María González', status: 'En Proceso' },
    { time: '11:00', title: 'Consulta general', pet: 'Luna', owner: 'Carlos Ramírez', status: 'Pendiente' },
    { time: '15:30', title: 'Vacunas', pet: 'Milo', owner: 'Ana Torres', status: 'Pendiente' },
    { time: '17:00', title: 'Peluquería', pet: 'Coco', owner: 'Jorge Valdés', status: 'Pendiente' },
  ];

  // CALENDARIO
  const today = new Date();
  const welcomeOptions = { weekday: 'long', day: 'numeric', month: 'long' };
  const formattedWelcome = today.toLocaleDateString('es-ES', welcomeOptions);
  const welcomeDate = formattedWelcome.charAt(0).toUpperCase() + formattedWelcome.slice(1);

  const [viewDate, setViewDate] = useState(new Date());

  const handlePrevMonth = () => setViewDate(prev => new Date(prev.getFullYear(), prev.getMonth() - 1, 1));
  const handleNextMonth = () => setViewDate(prev => new Date(prev.getFullYear(), prev.getMonth() + 1, 1));

  const viewMonth = viewDate.getMonth();
  const viewYear = viewDate.getFullYear();
  const monthName = viewDate.toLocaleDateString('es-ES', { month: 'long' });
  const calendarTitle = monthName.charAt(0).toUpperCase() + monthName.slice(1) + " " + viewYear;

  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  let firstDayIndex = new Date(viewYear, viewMonth, 1).getDay();
  firstDayIndex = firstDayIndex === 0 ? 6 : firstDayIndex - 1;

  const isCurrentMonth = today.getMonth() === viewMonth && today.getFullYear() === viewYear;
  const currentDay = today.getDate();

  const calendarCells = [];
  for (let i = 0; i < firstDayIndex; i++) {
    calendarCells.push(<span key={`prev-${i}`} style={{ color: '#D6DDE7' }}>-</span>);
  }
  for (let day = 1; day <= daysInMonth; day++) {
    const isToday = isCurrentMonth && day === currentDay;
    calendarCells.push(
      <span key={day} style={{ display: 'flex', justifyContent: 'center' }}>
        <span style={isToday ? { backgroundColor: '#FF7100', color: '#FFF', borderRadius: '50%', width: '22px', height: '22px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold' } : {}}>
          {day}
        </span>
      </span>
    );
  }

  const dropdownOptions = ['Esta semana', 'Semana pasada', 'Este mes', 'Este año'];

  return (
    <div className="panel-admin-container">
      <SidebarAdmin />
      <div className="main-wrapper">
        <HeaderAdmin />
        <main className="content">
          
          {/* Bienvenida */}
          <div className="welcome-section">
            <div>
              <h2>Bienvenida, Nataly</h2>
              <p>Aquí tienes un resumen claro de lo que sucede hoy en AmiDog.</p>
            </div>
            <div className="date-pill">
              <span className="dot"></span> {welcomeDate}
            </div>
          </div>
          
          {/* KPIs */}
          <section className="kpi-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '20px' }}>
            <KpiCardAdmin title="Reservas de hoy" value="12" subtext="↑ 20% vs. ayer" colorClass="teal" />
            <KpiCardAdmin title="Clientes registrados" value="248" subtext="↑ 15% vs. semana pasada" colorClass="orange-light" />
            <KpiCardAdmin title="Mascotas registradas" value="376" subtext="↑ 18% vs. semana pasada" colorClass="orange-dark" />
            
            <div className="kpi-hero-image" style={{ backgroundColor: '#fff', borderRadius: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <p style={{ color: '#999' }}>🐶 Ilustración Mascotas</p>
            </div>
          </section>

          {/* GRÁFICO DE LÍNEAS Y TABLA DE RESERVAS */}
          <section className="dashboard-sections" style={{ display: 'grid', gridTemplateColumns: '1.2fr 1.8fr', gap: '20px', marginTop: '20px' }}>
            
            {/* RESERVAS SEMANALES (CON DESPLEGABLE EXACTO DE TU IMAGEN) */}
            <div className="card" style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '16px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', position: 'relative' }}>
              <div>
                <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Icon name="calendar" size={18} />
                    <h3 className="card-title" style={{ fontSize: '16px', margin: 0, fontWeight: 'bold' }}>Reservas semanales</h3>
                  </div>

                  {/* MENÚ DESPLEGABLE EXACTO A TU MOCKUP */}
                  <div style={{ position: 'relative' }}>
                    <button 
                      onClick={() => setShowFilterDropdown(!showFilterDropdown)}
                      style={{ 
                        fontSize: '13px', 
                        cursor: 'pointer', 
                        color: '#6B7280', 
                        border: '1px solid #E5E7EB', 
                        padding: '6px 12px', 
                        borderRadius: '10px', 
                        background: '#FFF',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px'
                      }}
                    >
                      {filterPeriod} <span style={{ fontSize: '10px', color: '#9CA3AF' }}>▾</span>
                    </button>

                    {showFilterDropdown && (
                      <div style={{ 
                        position: 'absolute', 
                        right: 0, 
                        top: '36px', 
                        backgroundColor: '#FFF', 
                        border: '1px solid #E5E7EB', 
                        borderRadius: '12px', 
                        boxShadow: '0 10px 25px -5px rgba(0,0,0,0.08)', 
                        zIndex: 50,
                        minWidth: '140px',
                        overflow: 'hidden'
                      }}>
                        {dropdownOptions.map((opt, i) => (
                          <div 
                            key={opt}
                            onClick={() => { setFilterPeriod(opt); setShowFilterDropdown(false); }} 
                            style={{ 
                              padding: '10px 16px', 
                              fontSize: '13px', 
                              color: '#374151',
                              cursor: 'pointer',
                              borderBottom: i < dropdownOptions.length - 1 ? '1px solid #F3F4F6' : 'none',
                              backgroundColor: filterPeriod === opt ? '#F9FAFB' : '#FFF',
                              fontWeight: filterPeriod === opt ? '600' : '400'
                            }}
                          >
                            {opt}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>

                {/* SVG GRÁFICO */}
                <div style={{ width: '100%', height: '180px', marginTop: '10px' }}>
                  <svg viewBox="0 0 400 160" style={{ width: '100%', height: '100%', overflow: 'visible' }}>
                    <defs>
                      <linearGradient id="orangeGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#FF7100" stopOpacity="0.3" />
                        <stop offset="100%" stopColor="#FF7100" stopOpacity="0.0" />
                      </linearGradient>
                    </defs>

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

                    <polygon points={currentChart.polygon} fill="url(#orangeGradient)" />
                    <polyline fill="none" stroke="#FF7100" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" points={currentChart.points} />

                    {currentChart.dots.map((pt, idx) => (
                      <circle key={idx} cx={pt.x} cy={pt.y} r="4" fill="#FFFFFF" stroke="#FF7100" strokeWidth="2" />
                    ))}

                    {currentChart.labels.map((label, idx) => (
                      <text key={idx} x={30 + idx * 58} y="158" textAnchor="middle" fontSize="11" fill="#6B7280">{label}</text>
                    ))}
                  </svg>
                </div>
              </div>

              {/* LINK REPORTE COMPLETO */}
              <div style={{ textAlign: 'right', marginTop: '10px' }}>
                <button 
                  onClick={() => setActiveModal('report')}
                  style={{ background: 'none', border: 'none', color: '#08A89E', fontSize: '13px', fontWeight: 'bold', cursor: 'pointer' }}
                >
                  Ver reporte completo &gt;
                </button>
              </div>
            </div>

            {/* TABLA DE RESERVAS */}
            <BookingsTableAdmin />
          </section>

          {/* FILA INFERIOR */}
          <section className="dashboard-bottom-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '20px', marginTop: '20px' }}>
            
            {/* 1. SERVICIOS MÁS SOLICITADOS */}
            <div className="card" style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '16px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
              <div>
                <h3 className="card-title" style={{ fontSize: '16px', marginBottom: '15px', fontWeight: 'bold' }}>Servicios más solicitados</h3>
                
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px' }}>
                  <div style={{ position: 'relative', width: '110px', height: '110px' }}>
                    <svg viewBox="0 0 36 36" style={{ width: '100%', height: '100%', transform: 'rotate(-90deg)' }}>
                      <circle cx="18" cy="18" r="15.915" fill="transparent" stroke="#FF7100" strokeWidth="4.5" strokeDasharray="40 60" strokeDashoffset="0" />
                      <circle cx="18" cy="18" r="15.915" fill="transparent" stroke="#08A89E" strokeWidth="4.5" strokeDasharray="25 75" strokeDashoffset="-40" />
                      <circle cx="18" cy="18" r="15.915" fill="transparent" stroke="#F4A261" strokeWidth="4.5" strokeDasharray="20 80" strokeDashoffset="-65" />
                      <circle cx="18" cy="18" r="15.915" fill="transparent" stroke="#B0BEC5" strokeWidth="4.5" strokeDasharray="15 85" strokeDashoffset="-85" />
                    </svg>
                    <div style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                      <span style={{ fontSize: '9px', color: '#9CA3AF' }}>Total</span>
                      <span style={{ fontSize: '15px', fontWeight: 'bold', color: '#111418' }}>268</span>
                    </div>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '11px' }}>
                    <div><span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#FF7100', display: 'inline-block', marginRight: '4px' }}></span><b>Consulta general</b> (40%)</div>
                    <div><span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#08A89E', display: 'inline-block', marginRight: '4px' }}></span><b>Vacunas</b> (25%)</div>
                    <div><span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#F4A261', display: 'inline-block', marginRight: '4px' }}></span><b>Peluquería</b> (20%)</div>
                    <div><span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#B0BEC5', display: 'inline-block', marginRight: '4px' }}></span><b>Otros</b> (15%)</div>
                  </div>
                </div>
              </div>

              <div style={{ textAlign: 'center', marginTop: '15px' }}>
                <button 
                  onClick={() => setActiveModal('services')}
                  style={{ background: 'none', border: 'none', color: '#08A89E', fontSize: '12px', fontWeight: 'bold', cursor: 'pointer' }}
                >
                  Ver todos los servicios &gt;
                </button>
              </div>
            </div>

            {/* 2. AGENDA DE HOY */}
            <div className="card" style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '15px' }}>
                <Icon name="calendar" size={18} />
                <h3 className="card-title" style={{ fontSize: '16px', margin: 0, fontWeight: 'bold' }}>Agenda de hoy</h3>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', position: 'relative' }}>
                <div style={{ position: 'absolute', left: '52px', top: '10px', bottom: '15px', width: '2px', backgroundColor: '#E5E7EB', zIndex: 1 }}></div>

                {agendaEvents.map((item, idx) => (
                  <div 
                    key={idx} 
                    onClick={() => setSelectedAppointment(item)}
                    style={{ display: 'flex', alignItems: 'flex-start', gap: '12px', zIndex: 2, cursor: 'pointer', padding: '2px', borderRadius: '8px' }}
                  >
                    <span style={{ color: item.time === '17:00' ? '#FF7100' : '#08A89E', fontWeight: 'bold', fontSize: '12px', width: '38px' }}>{item.time}</span>
                    <span style={{ width: '10px', height: '10px', borderRadius: '50%', backgroundColor: item.time === '17:00' ? '#FF7100' : '#08A89E', marginTop: '3px', border: '2px solid #fff' }}></span>
                    <div>
                      <div style={{ fontWeight: 'bold', fontSize: '12px', color: '#111418' }}>{item.title}</div>
                      <div style={{ fontSize: '11px', color: '#9CA3AF' }}>{item.pet}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 3. FOTO GATITO */}
            <div className="card photo-card" style={{ backgroundColor: '#fae5d3', padding: 0, borderRadius: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
              <p style={{ color: '#d35400', fontSize: '14px' }}>🐱 Foto Gatito</p>
            </div>

            {/* 4. CALENDARIO */}
            <div className="card" style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                <span onClick={handlePrevMonth} style={{ cursor: 'pointer', padding: '4px' }}>
                  <Icon name="chevronLeft" size={18} />
                </span>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Icon name="calendar" size={16} />
                  <h3 className="card-title" style={{ fontSize: '15px', margin: 0, fontWeight: 'bold' }}>{calendarTitle}</h3>
                </div>
                <span onClick={handleNextMonth} style={{ cursor: 'pointer', padding: '4px' }}>
                  <Icon name="chevronRight" size={18} />
                </span>
              </div>
              
              <div style={{ width: '100%' }}>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', textAlign: 'center', fontSize: '11px', fontWeight: 'bold', color: '#A0AABF', marginBottom: '12px' }}>
                  <span>Lu</span><span>Ma</span><span>Mi</span><span>Ju</span><span>Vi</span><span>Sá</span><span>Do</span>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: '10px 0', textAlign: 'center', fontSize: '12px' }}>
                  {calendarCells}
                </div>
              </div>
            </div>

          </section>

          {/* MODAL: DETALLE DE SERVICIOS */}
          {activeModal === 'services' && (
            <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
              <div style={{ backgroundColor: '#FFF', borderRadius: '16px', width: '90%', maxWidth: '580px', padding: '24px 28px', boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #F3F4F6', paddingBottom: '16px', marginBottom: '24px' }}>
                  <h2 style={{ margin: 0, fontSize: '20px', fontWeight: 'bold', color: '#111827' }}>Detalle de servicios</h2>
                  <button onClick={() => setActiveModal(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#6B7280', display: 'flex', alignItems: 'center' }}>
                    <Icon name="close" size={20} />
                  </button>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  {[
                    { name: 'Consulta general', val: '107 (40%)', pct: '40%' },
                    { name: 'Vacunas', val: '67 (25%)', pct: '25%' },
                    { name: 'Peluquería', val: '54 (20%)', pct: '20%' },
                    { name: 'Otros', val: '40 (15%)', pct: '15%' },
                  ].map((item, idx) => (
                    <div key={idx} style={{ display: 'grid', gridTemplateColumns: '140px 1fr 100px', alignItems: 'center', gap: '16px' }}>
                      <span style={{ fontSize: '14px', color: '#374151', fontWeight: '500' }}>{item.name}</span>
                      <div style={{ width: '100%', backgroundColor: '#414141', height: '6px', borderRadius: '3px', overflow: 'hidden' }}>
                        <div style={{ width: item.pct, backgroundColor: '#20B2AA', height: '100%', borderRadius: '3px' }}></div>
                      </div>
                      <span style={{ fontSize: '14px', fontWeight: 'bold', textAlign: 'right', color: '#111827' }}>{item.val}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* MODAL: REPORTE (DINÁMICO SEGÚN EL PERÍODO SELECCIONADO) */}
          {activeModal === 'report' && (
            <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
              <div style={{ backgroundColor: '#FFF', borderRadius: '16px', width: '90%', maxWidth: '640px', padding: '24px 28px', boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #F3F4F6', paddingBottom: '16px', marginBottom: '24px' }}>
                  <h2 style={{ margin: 0, fontSize: '20px', fontWeight: 'bold', color: '#111827' }}>
                    Reporte · {filterPeriod}
                  </h2>
                  <button onClick={() => setActiveModal(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#6B7280', display: 'flex', alignItems: 'center' }}>
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

          {/* MODAL DETALLE CITA AGENDA */}
          {selectedAppointment && (
            <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
              <div style={{ backgroundColor: '#FFF', padding: '24px', borderRadius: '16px', maxWidth: '380px', width: '90%' }}>
                <h3 style={{ marginTop: 0, borderBottom: '1px solid #F3F4F6', paddingBottom: '10px' }}>Detalles de la Cita</h3>
                <p><b>Hora:</b> {selectedAppointment.time}</p>
                <p><b>Servicio:</b> {selectedAppointment.title}</p>
                <p><b>Mascota:</b> {selectedAppointment.pet}</p>
                <p><b>Dueño:</b> {selectedAppointment.owner}</p>
                <p><b>Estado:</b> <span style={{ color: '#08A89E', fontWeight: 'bold' }}>{selectedAppointment.status}</span></p>
                <div style={{ textAlign: 'right', marginTop: '20px' }}>
                  <button 
                    onClick={() => setSelectedAppointment(null)}
                    style={{ backgroundColor: '#08A89E', color: '#FFF', border: 'none', padding: '8px 16px', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold' }}
                  >
                    Cerrar
                  </button>
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
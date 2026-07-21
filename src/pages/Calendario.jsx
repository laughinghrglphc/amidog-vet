import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Header from '../components/Header'
import Footer from '../components/Footer'
import AppointmentHero from '../components/AppointmentHero'
import AppointmentSidebar from '../components/AppointmentSidebar'
import { getReservation, saveReservation } from '../utils/reservation'

const HORARIOS_DISPONIBLES = ['09:00', '09:30', '10:00', '10:30', '11:00', '11:30', '12:00', '14:00', '14:30', '15:00', '15:30', '16:00', '16:30', '17:00', '17:30', '18:00']
const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']

function formatDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function isPast(year, month, day) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return new Date(year, month, day) < today
}

export default function Calendario() {
  const navigate = useNavigate()
  const reservation = getReservation()
  const today = new Date()
  const [viewedMonth, setViewedMonth] = useState(new Date(today.getFullYear(), today.getMonth(), 1))
  const [selectedDate, setSelectedDate] = useState(reservation.fecha || '')
  const [selectedTime, setSelectedTime] = useState(reservation.hora || '')

  const days = useMemo(() => {
    const year = viewedMonth.getFullYear()
    const month = viewedMonth.getMonth()
    const firstDay = new Date(year, month, 1).getDay()
    const offset = firstDay === 0 ? 6 : firstDay - 1
    const daysInMonth = new Date(year, month + 1, 0).getDate()
    return [...Array(offset).fill(null), ...Array.from({ length: daysInMonth }, (_, index) => index + 1)]
  }, [viewedMonth])

  const changeMonth = (amount) => {
    setViewedMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1))
    setSelectedDate('')
    setSelectedTime('')
  }

  const handleContinue = () => {
    if (!selectedDate || !selectedTime) return
    saveReservation({ fecha: selectedDate, hora: selectedTime })
    navigate('/confirmacion')
  }

  const previousDisabled = viewedMonth.getFullYear() === today.getFullYear() && viewedMonth.getMonth() === today.getMonth()

  return (
    <>
      <Header />
      <AppointmentHero step={2} />
      <main className="main calendario-page">
        <div className="container">
          <div className="calendario-contenido">
            <section className="agenda" id="agenda-calendario">
              <h2 className="agenda__titulo">Elige el horario</h2>
              <div className="resumen" aria-label="Resumen de tu solicitud">
                <div className="resumen__item"><span className="resumen__icono"><img src="/assets/img/patitas.png" alt="" className="resumen-paw" aria-hidden="true" /></span><div><span className="resumen__label">Mascota</span><span className="resumen__valor">{reservation.mascota || 'Perro / Gato'}</span></div></div>
                <div className="resumen__item"><span className="resumen__icono" aria-hidden="true">🩺</span><div><span className="resumen__label">Servicio</span><span className="resumen__valor">{reservation.servicio || 'Consulta general'}</span></div></div>
                <div className="resumen__item"><span className="resumen__icono"><img src="/assets/img/LogoUsuarios.png" alt="" className="resumen-icon-img" aria-hidden="true" /></span><div><span className="resumen__label">Profesional</span><span className="resumen__valor">{reservation.profesional || 'Sin preferencia'}</span></div></div>
              </div>
              <div className="agenda__grid">
                <div className="bloque">
                  <h3 className="bloque__titulo">Fecha disponible</h3>
                  <div className="calendario">
                    <div className="calendario__cabecera">
                      <p className="calendario__mes" id="mes-texto">{MESES[viewedMonth.getMonth()]} {viewedMonth.getFullYear()}</p>
                      <div className="calendario__nav">
                        <button type="button" className="btn btn--circulo" id="btn-mes-anterior" aria-label="Mes anterior" disabled={previousDisabled} onClick={() => changeMonth(-1)}>‹</button>
                        <button type="button" className="btn btn--circulo" id="btn-mes-siguiente" aria-label="Mes siguiente" onClick={() => changeMonth(1)}>›</button>
                      </div>
                    </div>
                    <div className="calendario__dias-semana" aria-hidden="true"><span>Lu</span><span>Ma</span><span>Mi</span><span>Ju</span><span>Vi</span><span>Sá</span><span>Do</span></div>
                    <div className="calendario__dias" id="dias-calendario">
                      {days.map((day, index) => {
                        if (!day) return <div className="col" key={`empty-${index}`}></div>
                        const year = viewedMonth.getFullYear()
                        const month = viewedMonth.getMonth()
                        const date = new Date(year, month, day)
                        const dateValue = formatDate(date)
                        const unavailable = isPast(year, month, day) || date.getDay() === 0
                        return (
                          <div className="col text-center" key={dateValue}>
                            <button type="button" className={`dia-btn${selectedDate === dateValue ? ' seleccionado' : ''}`} disabled={unavailable} title={date.getDay() === 0 && !isPast(year, month, day) ? 'Sin atención este día' : undefined} onClick={() => { setSelectedDate(dateValue); setSelectedTime('') }}>{day}</button>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                </div>
                <div className="bloque">
                  <h3 className="bloque__titulo">Horarios disponibles</h3>
                  <div className="horarios" id="horarios-container">
                    {selectedDate && HORARIOS_DISPONIBLES.map((time) => (
                      <div className="col" key={time}><button type="button" className={`horario-btn${selectedTime === time ? ' seleccionado' : ''}`} onClick={() => setSelectedTime(time)}>{time}</button></div>
                    ))}
                  </div>
                </div>
              </div>
              <input type="hidden" id="input-fecha" name="fecha" value={selectedDate} readOnly />
              <input type="hidden" id="input-hora" name="hora" value={selectedTime} readOnly />
              <div className="agenda__acciones">
                <Link to="/reservar" className="btn btn-secundario">← Volver a datos</Link>
                <button type="button" className="btn btn-primario" id="btn-continuar" disabled={!(selectedDate && selectedTime)} onClick={handleContinue}>Continuar con confirmación →</button>
              </div>
            </section>
            <AppointmentSidebar />
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}

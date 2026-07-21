import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Header from '../components/Header'
import Footer from '../components/Footer'
import AppointmentHero from '../components/AppointmentHero'
import AppointmentSidebar from '../components/AppointmentSidebar'
import { confirmReservation, getReservation } from '../utils/reservation'

export default function Confirmacion() {
  const navigate = useNavigate()
  const reservation = getReservation()
  const [confirmed, setConfirmed] = useState(false)

  const handleConfirm = () => {
    confirmReservation(reservation)
    setConfirmed(true)
    document.dispatchEvent(new CustomEvent('amidog:reserva-confirmada', { detail: reservation }))
  }

  return (
    <>
      <Header />
      <AppointmentHero step={3} />
      <main className="main confirmacion-page">
        <div className="container">
          <div className="confirmacion-contenido">
            <section className="confirmacion-detalles" aria-labelledby="confirmacion-titulo">
              <h2 className="confirmacion-subtitulo" id="confirmacion-titulo">Confirma tu cita</h2>
              <p className="confirmacion-descripcion">Revisa que los datos estén correctos antes de finalizar la reserva.</p>
              <article className="confirmacion-bloque">
                <h3 className="confirmacion-bloque-titulo">Resumen de la atención</h3>
                <div className="confirmacion-grid-datos confirmacion-grid-datos--tres">
                  <div className="dato-item"><span className="dato-icono" aria-hidden="true"><img src="/assets/img/patitas.png" alt="" className="dato-icono-img" /></span><span className="dato-texto"><span className="dato-etiqueta">Mascota:</span><span className="dato-valor" id="resumen-mascota">{reservation.mascota || 'Perro / Gato'}</span></span></div>
                  <div className="dato-item"><span className="dato-icono" aria-hidden="true">🩺</span><span className="dato-texto"><span className="dato-etiqueta">Servicio:</span><span className="dato-valor" id="resumen-servicio">{reservation.servicio || 'Consulta general'}</span></span></div>
                  <div className="dato-item"><span className="dato-icono" aria-hidden="true"><img src="/assets/img/LogoUsuarios.png" alt="" className="dato-icono-img" /></span><span className="dato-texto"><span className="dato-etiqueta">Profesional:</span><span className="dato-valor" id="resumen-profesional">{reservation.profesional || 'Sin preferencia'}</span></span></div>
                </div>
              </article>
              <article className="confirmacion-bloque">
                <h3 className="confirmacion-bloque-titulo">Horario seleccionado</h3>
                <div className="confirmacion-grid-datos confirmacion-grid-datos--dos">
                  <div className="dato-item"><span className="dato-icono" aria-hidden="true"></span><span className="dato-texto"><span className="dato-etiqueta">Fecha:</span><span className="dato-valor" id="resumen-fecha">{reservation.fecha || '12 / 06 / 2025'}</span></span></div>
                  <div className="dato-item"><span className="dato-icono" aria-hidden="true"></span><span className="dato-texto"><span className="dato-etiqueta">Hora:</span><span className="dato-valor" id="resumen-hora">{reservation.hora || '09:00'}</span></span></div>
                </div>
              </article>
              <article className="confirmacion-bloque">
                <h3 className="confirmacion-bloque-titulo">Datos de contacto</h3>
                <div className="confirmacion-lista-datos">
                  <div className="dato-item dato-item--fila"><span className="dato-icono" aria-hidden="true"></span><span className="dato-etiqueta">Nombre:</span><span className="dato-valor" id="resumen-nombre">{reservation.nombre || 'Nombre y apellido'}</span></div>
                  <div className="dato-item dato-item--fila"><span className="dato-icono" aria-hidden="true"></span><span className="dato-etiqueta">Teléfono:</span><span className="dato-valor" id="resumen-telefono">{reservation.telefono || '+56 9 1234 5678'}</span></div>
                  <div className="dato-item dato-item--fila"><span className="dato-icono" aria-hidden="true"></span><span className="dato-etiqueta">Correo:</span><span className="dato-valor" id="resumen-correo">{reservation.correo || 'nombre@correo.cl'}</span></div>
                </div>
              </article>
              <div className="confirmacion-aviso"><img src="/assets/img/LogoWSP.webp" alt="" className="aviso-icono" aria-hidden="true" /><p className="aviso-texto">Te enviaremos la confirmación por WhatsApp o correo electrónico</p></div>
              <div className="confirmacion-acciones">
                <button type="button" className="btn btn-secundario" id="btn-volver" onClick={() => navigate('/calendario')}><span aria-hidden="true">←</span>Volver al horario</button>
                <button type="button" className={`btn btn-primario${confirmed ? ' btn--confirmado' : ''}`} id="btn-confirmar" disabled={confirmed} onClick={handleConfirm}>{confirmed ? 'Reserva confirmada' : 'Confirmar reserva'}</button>
              </div>
              <p className={`confirmacion-feedback${confirmed ? ' confirmacion-feedback--visible' : ''}`} id="confirmacion-feedback" role="status" aria-live="polite">{confirmed ? 'Reserva confirmada. Te contactaremos por WhatsApp o correo electrónico.' : ''}</p>
            </section>
            <AppointmentSidebar />
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Header from '../components/Header'
import Footer from '../components/Footer'
import AppointmentHero from '../components/AppointmentHero'
import AppointmentSidebar from '../components/AppointmentSidebar'
import { getReservation, saveReservation } from '../utils/reservation'

export default function Reservation() {
  const navigate = useNavigate()
  const saved = getReservation()
  const [form, setForm] = useState({
    nombreMascota: saved.nombreMascota || '',
    mascota: saved.mascota || 'Perro / Gato',
    servicio: saved.servicio || 'Consulta general',
    profesional: saved.profesional || 'Sin preferencia',
    fechaPreferida: saved.fechaPreferida || '',
    horarioPreferido: saved.horarioPreferido || 'Mañana / Tarde',
    nombre: saved.nombre || '',
    telefono: saved.telefono || '',
    correo: saved.correo || ''
  })

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    saveReservation(form)
    navigate('/calendario')
  }

  return (
    <>
      <Header />
      <AppointmentHero step={1} />
      <main className="main reserva-page">
        <div className="container">
          <div className="appointment">
            <form className="appointment-form" onSubmit={handleSubmit}>
              <section>
                <h2>Datos de la atención</h2>
                <p className="subtitle">Cuéntanos qué necesita tu mascota.</p>
                <div className="form-grid">
                  <div className="form-field"><label htmlFor="nombreMascota">Nombre de la mascota</label><input id="nombreMascota" name="nombreMascota" type="text" placeholder="Firulais" value={form.nombreMascota} onChange={handleChange} /></div>
                  <div className="form-field"><label htmlFor="mascota">Tipo de mascota</label><select id="mascota" name="mascota" value={form.mascota} onChange={handleChange}><option>Perro / Gato</option></select></div>
                  <div className="form-field"><label htmlFor="servicio">Servicio</label><select id="servicio" name="servicio" value={form.servicio} onChange={handleChange}><option>Consulta general</option></select></div>
                  <div className="form-field"><label htmlFor="profesional">Profesional</label><select id="profesional" name="profesional" value={form.profesional} onChange={handleChange}><option>Sin preferencia</option></select></div>
                  <div className="form-field"><label htmlFor="fechaPreferida">Fecha preferida</label><input id="fechaPreferida" name="fechaPreferida" type="text" placeholder="dd / mm / aaaa" value={form.fechaPreferida} onChange={handleChange} /></div>
                  <div className="form-field"><label htmlFor="horarioPreferido">Horario</label><select id="horarioPreferido" name="horarioPreferido" value={form.horarioPreferido} onChange={handleChange}><option>Mañana / Tarde</option></select></div>
                </div>
              </section>
              <section>
                <h2 id="datos-contacto-title">Tus datos de contacto</h2>
                <div className="form-grid">
                  <div className="form-field"><label htmlFor="nombre">Nombre completo</label><input id="nombre" name="nombre" type="text" placeholder="Nombre y apellido" value={form.nombre} onChange={handleChange} /></div>
                  <div className="form-field"><label htmlFor="telefono">Teléfono</label><input id="telefono" name="telefono" type="tel" placeholder="+56 9 1234 5678" value={form.telefono} onChange={handleChange} /></div>
                  <div className="form-field full-width"><label htmlFor="correo">Correo electrónico</label><input id="correo" name="correo" type="email" placeholder="nombre@correo.cl" value={form.correo} onChange={handleChange} /></div>
                </div>
              </section>
              <button className="btn btn-primario" type="submit">Continuar con el horario →</button>
            </form>
            <AppointmentSidebar />
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}

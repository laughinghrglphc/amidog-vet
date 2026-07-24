import { useState } from 'react'
import { todayValue } from '../../utils/panelDate'

function AppointmentForm({ pets, onSubmit, onCancel }) {
  const [form, setForm] = useState({
    petId: pets[0]?.id ?? '',
    service: 'Control general',
    veterinarian: 'Dra. Valentina Bustos',
    date: todayValue(),
    time: '10:00'
  })

  const update = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  const submit = (event) => {
    event.preventDefault()
    onSubmit(form)
  }

  return (
    <form className="dialog-form" onSubmit={submit}>
      <label>
        Mascota
        <select name="petId" value={form.petId} onChange={update} required>
          {pets.map((pet) => <option key={pet.id} value={pet.id}>{pet.name}</option>)}
        </select>
      </label>
      <label>
        Servicio
        <select name="service" value={form.service} onChange={update} required>
          <option>Control general</option>
          <option>Vacunación</option>
          <option>Desparasitación</option>
          <option>Consulta veterinaria</option>
          <option>Peluquería</option>
        </select>
      </label>
      <label>
        Veterinario/a
        <select name="veterinarian" value={form.veterinarian} onChange={update} required>
          <option>Dra. Valentina Bustos</option>
          <option>Dr. Martín Soto</option>
          <option>Dra. Camila Fuentes</option>
        </select>
      </label>
      <div className="form-grid">
        <label>
          Fecha
          <input name="date" type="date" min={todayValue()} value={form.date} onChange={update} required />
        </label>
        <label>
          Hora
          <input name="time" type="time" value={form.time} onChange={update} required />
        </label>
      </div>
      <div className="form-actions">
        <button className="button button--ghost" type="button" onClick={onCancel}>Cancelar</button>
        <button className="button" type="submit" disabled={!pets.length}>Agendar hora</button>
      </div>
    </form>
  )
}

export default AppointmentForm

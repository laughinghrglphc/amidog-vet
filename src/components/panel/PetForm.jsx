import { useState } from 'react'

function PetForm({ onSubmit, onCancel }) {
  const [form, setForm] = useState({ name: '', species: 'Perro', breed: '', age: '1', status: 'Sano' })
  const update = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  const submit = (event) => {
    event.preventDefault()
    onSubmit(form)
  }

  return (
    <form className="dialog-form" onSubmit={submit}>
      <label>
        Nombre
        <input name="name" value={form.name} onChange={update} placeholder="Nombre de la mascota" required />
      </label>
      <div className="form-grid">
        <label>
          Especie
          <select name="species" value={form.species} onChange={update}>
            <option>Perro</option>
            <option>Gato</option>
          </select>
        </label>
        <label>
          Edad
          <input name="age" type="number" min="0" max="30" value={form.age} onChange={update} required />
        </label>
      </div>
      <label>
        Raza
        <input name="breed" value={form.breed} onChange={update} placeholder="Ej.: Mestizo" required />
      </label>
      <label>
        Estado
        <select name="status" value={form.status} onChange={update}>
          <option>Sano</option>
          <option>En tratamiento</option>
          <option>Vacunas al día</option>
          <option>Esterilizada</option>
          <option>Esterilizado</option>
        </select>
      </label>
      <div className="form-actions">
        <button className="button button--ghost" type="button" onClick={onCancel}>Cancelar</button>
        <button className="button" type="submit">Registrar mascota</button>
      </div>
    </form>
  )
}

export default PetForm

import { useState } from 'react'
import { CLINIC_TIME_ZONE } from '../../utils/clinicTime'

function clinicToday() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    day: '2-digit',
    month: '2-digit',
    timeZone: CLINIC_TIME_ZONE,
    year: 'numeric',
  }).formatToParts(new Date())
  const value = (type) => parts.find((part) => part.type === type)?.value ?? ''
  return `${value('year')}-${value('month')}-${value('day')}`
}

function PetForm({
  initialPet = null,
  onSubmit,
  onCancel,
  pending = false,
}) {
  const [form, setForm] = useState({
    birthdate: initialPet?.birthdate ?? '',
    breed: initialPet?.breed ?? '',
    name: initialPet?.name ?? '',
    species: initialPet?.species ?? 'Perro',
  })
  const [validationError, setValidationError] = useState('')

  const update = (event) => {
    setForm((current) => ({
      ...current,
      [event.target.name]: event.target.value,
    }))
  }

  const submit = (event) => {
    event.preventDefault()
    const name = form.name.trim()
    const species = form.species.trim()
    const breed = form.breed.trim()
    if (!name) {
      setValidationError('Ingresa el nombre de la mascota.')
      return
    }
    if (name.length > 80) {
      setValidationError('El nombre puede tener hasta 80 caracteres.')
      return
    }
    if (!species) {
      setValidationError('Ingresa la especie de la mascota.')
      return
    }
    if (species.length > 40) {
      setValidationError('La especie puede tener hasta 40 caracteres.')
      return
    }
    if (breed.length > 80) {
      setValidationError('La raza puede tener hasta 80 caracteres.')
      return
    }
    if (form.birthdate && form.birthdate > clinicToday()) {
      setValidationError('La fecha de nacimiento no puede estar en el futuro.')
      return
    }
    setValidationError('')
    onSubmit({
      birthdate: form.birthdate || null,
      breed: breed || null,
      name,
      species,
    })
  }

  return (
    <form className="dialog-form" noValidate onSubmit={submit}>
      {validationError && <p className="form-feedback" role="alert">{validationError}</p>}
      <label>
        Nombre
        <input
          name="name"
          maxLength="80"
          value={form.name}
          onChange={update}
          placeholder="Nombre de la mascota"
          required
        />
      </label>
      <label>
        Especie
        <input
          name="species"
          maxLength="40"
          value={form.species}
          onChange={update}
          placeholder="Ej.: Perro"
          required
        />
      </label>
      <label>
        Raza (opcional)
        <input
          name="breed"
          maxLength="80"
          value={form.breed}
          onChange={update}
          placeholder="Ej.: Mestizo"
        />
      </label>
      <label>
        Fecha de nacimiento (opcional)
        <input
          name="birthdate"
          type="date"
          max={clinicToday()}
          value={form.birthdate}
          onChange={update}
        />
      </label>
      <div className="form-actions">
        <button
          className="button button--ghost"
          type="button"
          disabled={pending}
          onClick={onCancel}
        >
          Cancelar
        </button>
        <button className="button" type="submit" disabled={pending}>
          {pending
            ? 'Guardando…'
            : initialPet ? 'Guardar mascota' : 'Registrar mascota'}
        </button>
      </div>
    </form>
  )
}

export default PetForm

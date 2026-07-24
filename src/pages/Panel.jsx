import { useEffect, useMemo, useState } from 'react'
import PanelHeader from '../components/panel/PanelHeader'
import Hero from '../components/panel/Hero'
import Summary from '../components/panel/Summary'
import Appointments from '../components/panel/Appointments'
import Pets from '../components/panel/Pets'
import PanelFooter from '../components/panel/PanelFooter'
import Modal from '../components/panel/Modal'
import AppointmentForm from '../components/panel/AppointmentForm'
import PetForm from '../components/panel/PetForm'
import AppointmentCard from '../components/panel/AppointmentCard'
import PetCard from '../components/panel/PetCard'
import Toast from '../components/panel/Toast'
import { formatDate } from '../utils/panelDate'

const initialAppointments = [
  {
    id: 1,
    petId: 1,
    pet: 'Max',
    kind: 'Golden retriever',
    tag: 'Perro',
    service: 'Vacunación',
    veterinarian: 'Dra. Valentina Bustos',
    date: '2026-07-25',
    time: '10:30',
    image: 'max'
  },
  {
    id: 2,
    petId: 2,
    pet: 'Annie',
    kind: 'Gato doméstico',
    tag: 'Gato',
    service: 'Control general y examen físico',
    veterinarian: 'Dr. Martín Soto',
    date: '2026-08-02',
    time: '15:00',
    image: 'annie'
  }
]

const initialPets = [
  {
    id: 1,
    name: 'Max',
    breed: 'Golden retriever',
    age: '3 años',
    tag: 'Perro',
    status: 'Sano',
    image: 'max'
  },
  {
    id: 2,
    name: 'Annie',
    breed: 'Gato doméstico',
    age: '2 años',
    tag: 'Gato',
    status: 'Esterilizada',
    image: 'annie'
  }
]

function readStorage(key, fallback) {
  try {
    const saved = localStorage.getItem(key)
    return saved ? JSON.parse(saved) : fallback
  } catch {
    return fallback
  }
}

export default function Panel() {
  const [appointments, setAppointments] = useState(() => readStorage('amidog-appointments', initialAppointments))
  const [pets, setPets] = useState(() => readStorage('amidog-pets', initialPets))
  const [dialog, setDialog] = useState(null)
  const [toast, setToast] = useState('')

  useEffect(() => {
    localStorage.setItem('amidog-appointments', JSON.stringify(appointments))
  }, [appointments])

  useEffect(() => {
    localStorage.setItem('amidog-pets', JSON.stringify(pets))
  }, [pets])

  useEffect(() => {
    if (!toast) return undefined
    const timer = window.setTimeout(() => setToast(''), 2600)
    return () => window.clearTimeout(timer)
  }, [toast])

  const sortedAppointments = useMemo(
    () => [...appointments].sort((a, b) => `${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`)),
    [appointments]
  )

  const nextAppointment = sortedAppointments[0] ?? null

  const scheduleAppointment = (data) => {
    const selectedPet = pets.find((pet) => pet.id === Number(data.petId))
    if (!selectedPet) return

    setAppointments((current) => [
      ...current,
      {
        id: Date.now(),
        petId: selectedPet.id,
        pet: selectedPet.name,
        kind: selectedPet.breed,
        tag: selectedPet.tag,
        service: data.service,
        veterinarian: data.veterinarian,
        date: data.date,
        time: data.time,
        image: selectedPet.image
      }
    ])
    setDialog(null)
    setToast('La hora fue agendada correctamente.')
  }

  const registerPet = (data) => {
    const tag = data.species === 'Gato' ? 'Gato' : 'Perro'
    setPets((current) => [
      ...current,
      {
        id: Date.now(),
        name: data.name.trim(),
        breed: data.breed.trim(),
        age: `${data.age} ${Number(data.age) === 1 ? 'año' : 'años'}`,
        tag,
        status: data.status,
        image: tag === 'Gato' ? 'annie' : 'max'
      }
    ])
    setDialog(null)
    setToast('La mascota fue registrada correctamente.')
  }

  const requestCancellation = (appointment) => {
    setDialog({ type: 'cancel', appointment })
  }

  const confirmCancellation = () => {
    setAppointments((current) => current.filter((item) => item.id !== dialog.appointment.id))
    setDialog(null)
    setToast('La hora fue anulada.')
  }

  const requestPetDeletion = (pet) => {
    setDialog({ type: 'delete-pet', pet })
  }

  const confirmPetDeletion = () => {
    const petId = dialog.pet.id
    const petName = dialog.pet.name

    setPets((current) => current.filter((pet) => pet.id !== petId))
    setAppointments((current) => current.filter((appointment) => appointment.petId !== petId))
    setDialog(null)
    setToast(`${petName} fue eliminado de tus mascotas.`)
  }

  const modalContent = () => {
    if (!dialog) return null

    if (dialog.type === 'appointment-form') {
      return (
        <Modal title="Agendar nueva hora" onClose={() => setDialog(null)}>
          <AppointmentForm pets={pets} onSubmit={scheduleAppointment} onCancel={() => setDialog(null)} />
        </Modal>
      )
    }

    if (dialog.type === 'pet-form') {
      return (
        <Modal title="Registrar nueva mascota" onClose={() => setDialog(null)}>
          <PetForm onSubmit={registerPet} onCancel={() => setDialog(null)} />
        </Modal>
      )
    }

    if (dialog.type === 'all-appointments') {
      return (
        <Modal title="Todas mis horas" onClose={() => setDialog(null)} wide>
          <div className="modal-list">
            {sortedAppointments.length ? sortedAppointments.map((appointment) => (
              <AppointmentCard key={appointment.id} appointment={appointment} onCancel={requestCancellation} />
            )) : <p className="modal-empty">No tienes horas agendadas.</p>}
          </div>
        </Modal>
      )
    }

    if (dialog.type === 'all-pets') {
      return (
        <Modal title="Mis mascotas registradas" onClose={() => setDialog(null)}>
          <div className="modal-list">
            {pets.map((pet) => (
              <PetCard key={pet.id} pet={pet} onView={(selectedPet) => setDialog({ type: 'pet-detail', pet: selectedPet })} />
            ))}
          </div>
        </Modal>
      )
    }

    if (dialog.type === 'pet-detail') {
      const { pet } = dialog
      return (
        <Modal title={`Ficha de ${pet.name}`} onClose={() => setDialog(null)}>
          <div className="pet-detail">
            <PetCard pet={pet} compact />
            <dl>
              <div><dt>Nombre</dt><dd>{pet.name}</dd></div>
              <div><dt>Especie</dt><dd>{pet.tag}</dd></div>
              <div><dt>Raza</dt><dd>{pet.breed}</dd></div>
              <div><dt>Edad</dt><dd>{pet.age}</dd></div>
              <div><dt>Estado</dt><dd>{pet.status}</dd></div>
            </dl>
            <div className="pet-detail__actions">
              <button className="button button--secondary" type="button" onClick={() => requestPetDeletion(pet)}>
                Eliminar mascota
              </button>
            </div>
          </div>
        </Modal>
      )
    }

    if (dialog.type === 'delete-pet') {
      return (
        <Modal title="Eliminar mascota" onClose={() => setDialog(null)}>
          <div className="confirmation">
            <p>¿Deseas eliminar a <strong>{dialog.pet.name}</strong> de tus mascotas registradas? También se eliminarán sus horas agendadas.</p>
            <div className="form-actions">
              <button className="button button--ghost" type="button" onClick={() => setDialog(null)}>Volver</button>
              <button className="button button--secondary" type="button" onClick={confirmPetDeletion}>Sí, eliminar</button>
            </div>
          </div>
        </Modal>
      )
    }

    if (dialog.type === 'cancel') {
      return (
        <Modal title="Anular hora" onClose={() => setDialog(null)}>
          <div className="confirmation">
            <p>¿Deseas anular la hora de <strong>{dialog.appointment.pet}</strong> para el {formatDate(dialog.appointment.date)} a las {dialog.appointment.time} hrs?</p>
            <div className="form-actions">
              <button className="button button--ghost" type="button" onClick={() => setDialog(null)}>Volver</button>
              <button className="button button--secondary" type="button" onClick={confirmCancellation}>Sí, anular</button>
            </div>
          </div>
        </Modal>
      )
    }

    return null
  }

  return (
    <div className="panel-page">
      <div className="page-shell">
        <div className="app-frame">
          <PanelHeader onProfileAction={(message) => setToast(message)} />
          <main className="dashboard">
            <div className="dashboard__surface">
              <Hero />
              <Summary
                appointmentCount={appointments.length}
                petCount={pets.length}
                nextAppointment={nextAppointment}
                onViewAppointments={() => setDialog({ type: 'all-appointments' })}
                onViewPets={() => setDialog({ type: 'all-pets' })}
              />
              <div className="dashboard__columns">
                <Appointments
                  appointments={sortedAppointments}
                  onCancel={requestCancellation}
                  onViewAll={() => setDialog({ type: 'all-appointments' })}
                  onAdd={() => setDialog({ type: 'appointment-form' })}
                />
                <Pets
                  pets={pets}
                  onView={(pet) => setDialog({ type: 'pet-detail', pet })}
                  onViewAll={() => setDialog({ type: 'all-pets' })}
                  onAdd={() => setDialog({ type: 'pet-form' })}
                />
              </div>
            </div>
          </main>
          <PanelFooter />
        </div>
      </div>
      {modalContent()}
      <Toast message={toast} />
    </div>
  )
}

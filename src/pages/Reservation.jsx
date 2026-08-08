import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'
import { Link, useNavigate } from 'react-router'
import { bookingApi } from '../api/bookingApi'
import Header from '../components/Header'
import Footer from '../components/Footer'
import AppointmentHero from '../components/AppointmentHero'
import AppointmentSidebar from '../components/AppointmentSidebar'
import { useBookingDraft } from '../hooks/useBookingDraft'

const MAX_PETS = 10
const MAX_NOTE_LENGTH = 500
const LOAD_ERROR = 'No pudimos cargar tus mascotas y servicios.'

function safeMessage(error, fallback) {
  return error instanceof Error && error.message.trim() ? error.message : fallback
}

function activeRows(rows) {
  return (Array.isArray(rows) ? rows : []).filter((row) => row?.active !== false)
}

export default function Reservation({ api = bookingApi }) {
  const navigate = useNavigate()
  const { clear, draft, save } = useBookingDraft()
  const [pets, setPets] = useState([])
  const [services, setServices] = useState([])
  const [items, setItems] = useState(draft.items)
  const [note, setNote] = useState(draft.note)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [feedback, setFeedback] = useState('')
  const mountedRef = useRef(false)
  const generationRef = useRef(0)
  const draftRef = useRef(draft)
  const continuingRef = useRef(false)

  useEffect(() => {
    draftRef.current = draft
  }, [draft])

  const loadResources = useCallback(async () => {
    const generation = generationRef.current + 1
    generationRef.current = generation
    if (mountedRef.current) {
      setLoading(true)
      setLoadError('')
    }

    try {
      const [petRows, serviceRows] = await Promise.all([
        api.pets(),
        api.services(),
      ])
      if (!mountedRef.current || generationRef.current !== generation) {
        return
      }
      const currentPets = activeRows(petRows)
      const currentServices = activeRows(serviceRows)
      const petIds = new Set(currentPets.map(({ id }) => Number(id)))
      const serviceIds = new Set(currentServices.map(({ id }) => Number(id)))
      const currentDraft = draftRef.current
      const reconciled = currentDraft.items.filter(({ petId, serviceId }) => (
        petIds.has(petId) && serviceIds.has(serviceId)
      ))

      setPets(currentPets)
      setServices(currentServices)
      setItems(reconciled)
      if (reconciled.length !== currentDraft.items.length) {
        save({ ...currentDraft, items: reconciled })
        setFeedback(
          'Algunas selecciones ya no están disponibles. Revisa tus mascotas y servicios.',
        )
      }
    } catch (error) {
      if (mountedRef.current && generationRef.current === generation) {
        setLoadError(safeMessage(error, LOAD_ERROR))
      }
    } finally {
      if (mountedRef.current && generationRef.current === generation) {
        setLoading(false)
      }
    }
  }, [api, save])

  useEffect(() => {
    mountedRef.current = true
    let cancelled = false
    queueMicrotask(() => {
      if (!cancelled) {
        void loadResources()
      }
    })
    return () => {
      cancelled = true
      mountedRef.current = false
      generationRef.current += 1
    }
  }, [loadResources])

  const persistSafeSelection = (nextItems, nextNote = note) => {
    const completeItems = nextItems.filter(({ petId, serviceId }) => (
      Number.isInteger(petId)
      && petId > 0
      && Number.isInteger(serviceId)
      && serviceId > 0
    ))
    save({
      items: completeItems,
      startsAt: draft.startsAt,
      note: nextNote,
    })
  }

  const togglePet = (petId, checked) => {
    setFeedback('')
    if (checked && items.length >= MAX_PETS) {
      setFeedback('Puedes reservar para un máximo 10 mascotas a la vez.')
      return
    }
    const nextItems = checked
      ? [...items, { petId, serviceId: null }]
      : items.filter((item) => item.petId !== petId)
    setItems(nextItems)
    persistSafeSelection(nextItems)
  }

  const selectService = (petId, value) => {
    setFeedback('')
    const serviceId = Number(value)
    const nextItems = items.map((item) => (
      item.petId === petId
        ? { ...item, serviceId: Number.isInteger(serviceId) && serviceId > 0
          ? serviceId
          : null }
        : item
    ))
    setItems(nextItems)
    persistSafeSelection(nextItems)
  }

  const handleNote = (event) => {
    const nextNote = event.target.value
    setNote(nextNote)
    if (nextNote.length <= MAX_NOTE_LENGTH) {
      persistSafeSelection(items, nextNote)
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    if (continuingRef.current) {
      return
    }
    continuingRef.current = true
    setFeedback('')
    const missingService = items.find(({ serviceId }) => !serviceId)
    const missingPet = missingService
      ? pets.find(({ id }) => Number(id) === missingService.petId)
      : null

    if (items.length === 0) {
      setFeedback('Selecciona al menos una mascota.')
      continuingRef.current = false
      return
    }
    if (missingService) {
      setFeedback(`Selecciona un servicio para ${missingPet?.name ?? 'cada mascota'}.`)
      continuingRef.current = false
      return
    }
    const normalizedNote = note.trim()
    if (normalizedNote.length > MAX_NOTE_LENGTH) {
      setFeedback('La nota permite un máximo 500 caracteres.')
      continuingRef.current = false
      return
    }

    save({
      items,
      startsAt: draft.startsAt,
      note: normalizedNote,
    })
    navigate('/calendario')
  }

  const resetDraft = () => {
    clear()
    setItems([])
    setNote('')
    setFeedback('La selección fue limpiada.')
  }

  return (
    <>
      <Header />
      <AppointmentHero step={1} />
      <main className="main reserva-page">
        <div className="container">
          <div className="appointment">
            <section className="appointment-form" aria-labelledby="booking-details-title">
              <h2 id="booking-details-title">Elige tus mascotas y servicios</h2>
              <p className="subtitle">
                Puedes incluir hasta 10 mascotas registradas. Cada una necesita un servicio.
              </p>

              {loading && (
                <p className="booking-state" role="status" aria-live="polite">
                  Cargando mascotas y servicios…
                </p>
              )}

              {!loading && loadError && (
                <div className="booking-state booking-state--error">
                  <p role="alert">{loadError}</p>
                  <button type="button" className="btn btn-secundario" onClick={loadResources}>
                    Reintentar
                  </button>
                </div>
              )}

              {!loading && !loadError && pets.length === 0 && (
                <div className="booking-state">
                  <p>No tienes mascotas activas registradas.</p>
                  <Link className="btn btn-secundario" to="/panel">
                    Registrar una mascota
                  </Link>
                </div>
              )}

              {!loading && !loadError && services.length === 0 && (
                <p className="booking-state booking-state--error" role="alert">
                  No hay servicios disponibles en este momento. Intenta nuevamente más tarde.
                </p>
              )}

              {!loading && !loadError && pets.length > 0 && (
                <form onSubmit={handleSubmit}>
                  <fieldset className="pet-service-list" disabled={services.length === 0}>
                    <legend>Mascotas registradas</legend>
                    {pets.map((pet) => {
                      const selected = items.find(({ petId }) => petId === Number(pet.id))
                      return (
                        <article className="pet-service-row" key={pet.id}>
                          <label className="pet-choice">
                            <input
                              type="checkbox"
                              aria-label={pet.name}
                              checked={Boolean(selected)}
                              onChange={(event) => togglePet(
                                Number(pet.id),
                                event.target.checked,
                              )}
                            />
                            <span>
                              <strong>{pet.name}</strong>
                              <small>{pet.species}{pet.breed ? ` · ${pet.breed}` : ''}</small>
                            </span>
                          </label>
                          {selected && (
                            <div className="form-field pet-service-field">
                              <label htmlFor={`service-${pet.id}`}>
                                Servicio para {pet.name}
                              </label>
                              <select
                                id={`service-${pet.id}`}
                                value={selected.serviceId ?? ''}
                                onChange={(event) => selectService(
                                  Number(pet.id),
                                  event.target.value,
                                )}
                              >
                                <option value="">Selecciona un servicio</option>
                                {services.map((service) => (
                                  <option key={service.id} value={service.id}>
                                    {service.name}
                                  </option>
                                ))}
                              </select>
                            </div>
                          )}
                        </article>
                      )
                    })}
                  </fieldset>

                  <div className="form-field booking-note">
                    <label htmlFor="booking-note">Nota para la clínica (opcional)</label>
                    <textarea
                      id="booking-note"
                      maxLength={MAX_NOTE_LENGTH + 1}
                      value={note}
                      onChange={handleNote}
                      rows="4"
                    />
                    <small>{Math.min(note.length, MAX_NOTE_LENGTH + 1)} / 500</small>
                  </div>

                  {feedback && (
                    <p
                      className={feedback.includes('limpiada')
                        ? 'booking-feedback'
                        : 'booking-feedback booking-feedback--error'}
                      role={feedback.includes('limpiada') ? 'status' : 'alert'}
                    >
                      {feedback}
                    </p>
                  )}

                  <div className="booking-actions">
                    <button
                      className="btn btn-secundario"
                      type="button"
                      onClick={resetDraft}
                    >
                      Limpiar selección
                    </button>
                    <button
                      className="btn btn-primario"
                      type="submit"
                      disabled={services.length === 0}
                    >
                      Continuar con el horario →
                    </button>
                  </div>
                </form>
              )}
            </section>
            <AppointmentSidebar />
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}

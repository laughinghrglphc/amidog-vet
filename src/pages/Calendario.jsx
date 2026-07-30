import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { bookingApi } from '../api/bookingApi'
import Header from '../components/Header'
import Footer from '../components/Footer'
import AppointmentHero from '../components/AppointmentHero'
import AppointmentSidebar from '../components/AppointmentSidebar'
import BookingCalendar from '../components/BookingCalendar'
import { useBookingDraft } from '../hooks/useBookingDraft'
import {
  bookingAvailabilityRange,
  clinicDateValue,
  groupAvailabilitySlots,
} from '../utils/reservation'

const LOAD_ERROR = 'No pudimos cargar la disponibilidad.'

function safeMessage(error, fallback) {
  return error instanceof Error && error.message.trim() ? error.message : fallback
}

function activeIds(rows) {
  return new Set(
    (Array.isArray(rows) ? rows : [])
      .filter((row) => row?.active !== false)
      .map((row) => Number(row?.id))
      .filter((id) => Number.isInteger(id) && id > 0),
  )
}

function completeItems(items) {
  if (!Array.isArray(items) || items.length < 1 || items.length > 10) {
    return false
  }
  const pets = new Set()
  return items.every(({ petId, serviceId }) => {
    const valid = Number.isInteger(petId)
      && petId > 0
      && Number.isInteger(serviceId)
      && serviceId > 0
      && !pets.has(petId)
    pets.add(petId)
    return valid
  })
}

export default function Calendario({ api = bookingApi }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { clearStart, draft, save } = useBookingDraft()
  const [availabilityRange] = useState(() => bookingAvailabilityRange(new Date()))
  const [slots, setSlots] = useState([])
  const [selectedStart, setSelectedStart] = useState(draft.startsAt)
  const [selectedDate, setSelectedDate] = useState(
    () => clinicDateValue(draft.startsAt),
  )
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [feedback, setFeedback] = useState(
    typeof location.state?.collisionMessage === 'string'
      ? location.state.collisionMessage
      : '',
  )
  const draftRef = useRef(draft)
  const mountedRef = useRef(false)
  const generationRef = useRef(0)
  const selectedStartRef = useRef(draft.startsAt)
  const selectedDateRef = useRef(clinicDateValue(draft.startsAt))
  const continuingRef = useRef(false)

  useEffect(() => {
    draftRef.current = draft
  }, [draft])

  const loadAvailability = useCallback(async () => {
    const currentDraft = draftRef.current
    if (!completeItems(currentDraft.items)) {
      navigate('/reservar', { replace: true })
      return
    }

    const generation = generationRef.current + 1
    generationRef.current = generation
    if (mountedRef.current) {
      setLoading(true)
      setLoadError('')
    }

    try {
      const [pets, services] = await Promise.all([
        api.pets(),
        api.services(),
      ])
      if (!mountedRef.current || generationRef.current !== generation) {
        return
      }
      const petIds = activeIds(pets)
      const serviceIds = activeIds(services)
      const validItems = currentDraft.items.filter(({ petId, serviceId }) => (
        petIds.has(petId) && serviceIds.has(serviceId)
      ))
      if (validItems.length !== currentDraft.items.length) {
        selectedStartRef.current = ''
        save({ ...currentDraft, items: validItems, startsAt: '' })
        navigate('/reservar', { replace: true })
        return
      }

      const available = await api.availability(
        availabilityRange.from,
        availabilityRange.to,
      )
      if (!mountedRef.current || generationRef.current !== generation) {
        return
      }
      const currentSlots = (Array.isArray(available) ? available : [])
        .filter((slot) => (
          slot
          && typeof slot.startsAt === 'string'
          && Number.isFinite(Date.parse(slot.startsAt))
        ))
      setSlots(currentSlots)

      const currentSelection = selectedStartRef.current
      const availableDates = new Set(
        currentSlots.map(({ startsAt }) => clinicDateValue(startsAt)),
      )
      if (currentSelection
        && !currentSlots.some(({ startsAt }) => startsAt === currentSelection)) {
        selectedStartRef.current = ''
        setSelectedStart('')
        clearStart()
        setFeedback('El horario seleccionado ya no está disponible. Elige otro bloque.')
      }
      if (currentSelection
        && currentSlots.some(({ startsAt }) => startsAt === currentSelection)) {
        const currentDate = clinicDateValue(currentSelection)
        selectedDateRef.current = currentDate
        setSelectedDate(currentDate)
      } else if (selectedDateRef.current
        && !availableDates.has(selectedDateRef.current)) {
        selectedDateRef.current = ''
        setSelectedDate('')
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
  }, [api, availabilityRange, clearStart, navigate, save])

  useEffect(() => {
    mountedRef.current = true
    let cancelled = false
    queueMicrotask(() => {
      if (!cancelled) {
        void loadAvailability()
      }
    })
    return () => {
      cancelled = true
      mountedRef.current = false
      generationRef.current += 1
    }
  }, [loadAvailability])

  const groups = useMemo(() => groupAvailabilitySlots(slots), [slots])
  const selectedGroup = useMemo(
    () => groups.find((group) => group.dateValue === selectedDate) ?? null,
    [groups, selectedDate],
  )

  const chooseDate = (dateValue) => {
    selectedDateRef.current = dateValue
    setSelectedDate(dateValue)
    if (selectedStartRef.current
      && clinicDateValue(selectedStartRef.current) !== dateValue) {
      selectedStartRef.current = ''
      setSelectedStart('')
      clearStart()
    }
    setFeedback('')
  }

  const chooseSlot = (startsAt) => {
    selectedStartRef.current = startsAt
    setSelectedStart(startsAt)
    save({ ...draftRef.current, startsAt })
    setFeedback('')
  }

  const handleContinue = () => {
    if (continuingRef.current) {
      return
    }
    continuingRef.current = true
    const currentSelection = selectedStartRef.current
    if (!slots.some(({ startsAt }) => startsAt === currentSelection)) {
      setFeedback('Selecciona un horario disponible.')
      continuingRef.current = false
      return
    }
    save({ ...draftRef.current, startsAt: currentSelection })
    navigate('/confirmacion')
  }

  return (
    <>
      <Header />
      <AppointmentHero step={2} />
      <main className="main calendario-page">
        <div className="container">
          <div className="calendario-contenido">
            <section className="agenda" aria-labelledby="availability-title">
              <div className="availability-heading">
                <div>
                  <h2 className="agenda__titulo" id="availability-title">
                    Elige un horario disponible
                  </h2>
                  <p>Todos los horarios duran 30 minutos y provienen de la agenda actual.</p>
                </div>
                <button
                  type="button"
                  className="btn btn-secundario"
                  onClick={loadAvailability}
                >
                  Actualizar disponibilidad
                </button>
              </div>

              {loading && (
                <p className="booking-state" role="status" aria-live="polite">
                  Cargando disponibilidad…
                </p>
              )}

              {!loading && loadError && (
                <div className="booking-state booking-state--error">
                  <p role="alert">{loadError}</p>
                  <button
                    type="button"
                    className="btn btn-secundario"
                    onClick={loadAvailability}
                  >
                    Reintentar
                  </button>
                </div>
              )}

              {feedback && (
                <p className="booking-feedback booking-feedback--error" role="alert">
                  {feedback}
                </p>
              )}

              {!loading && !loadError && groups.length === 0 && (
                <p className="booking-state">
                  No hay horarios disponibles en el rango consultado.
                </p>
              )}

              {!loadError && groups.length > 0 && (
                <div className="agenda__grid">
                  <div>
                    <h3 className="bloque__titulo">1. Selecciona una fecha</h3>
                    <BookingCalendar
                      availableDates={groups.map((group) => group.dateValue)}
                      from={availabilityRange.from}
                      to={availabilityRange.to}
                      selectedDate={selectedDate}
                      onSelectDate={chooseDate}
                    />
                  </div>
                  <section
                    className="availability-times"
                    aria-labelledby="available-times-title"
                    aria-live="polite"
                  >
                    <h3 className="bloque__titulo" id="available-times-title">
                      2. Elige un horario
                    </h3>
                    {selectedGroup ? (
                      <>
                        <p className="availability-times__date">
                          {selectedGroup.date}
                        </p>
                      <div className="horarios">
                        {selectedGroup.slots.map((slot) => (
                          <button
                            type="button"
                            className={`horario-btn${
                              selectedStart === slot.startsAt ? ' seleccionado' : ''
                            }`}
                            aria-label={`${selectedGroup.date} a las ${slot.time}`}
                            aria-pressed={selectedStart === slot.startsAt}
                            key={slot.startsAt}
                            onClick={() => chooseSlot(slot.startsAt)}
                          >
                            {slot.time}
                          </button>
                        ))}
                      </div>
                      </>
                    ) : (
                      <p className="horarios__mensaje">
                        Selecciona un día disponible para ver sus horarios.
                      </p>
                    )}
                  </section>
                </div>
              )}

              <div className="agenda__acciones">
                <Link to="/reservar" className="btn btn-secundario">
                  ← Volver a mascotas
                </Link>
                <button
                  type="button"
                  className="btn btn-primario"
                  disabled={!selectedStart || loading}
                  onClick={handleContinue}
                >
                  Continuar con la confirmación →
                </button>
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

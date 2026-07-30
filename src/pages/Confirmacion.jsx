import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'
import { Link, useNavigate } from 'react-router'
import { bookingApi } from '../api/bookingApi'
import { ApiError } from '../api/http'
import Header from '../components/Header'
import Footer from '../components/Footer'
import AppointmentHero from '../components/AppointmentHero'
import AppointmentSidebar from '../components/AppointmentSidebar'
import {
  clearPersistedBookingDraft,
  clearPersistedBookingStart,
  useBookingDraft,
} from '../hooks/useBookingDraft'
import { formatClinicDateTime } from '../utils/clinicTime'

const LOAD_ERROR = 'No pudimos revisar tus mascotas y servicios.'
const SUBMIT_ERROR = 'No pudimos registrar la reserva.'

function safeMessage(error, fallback) {
  return error instanceof Error && error.message.trim() ? error.message : fallback
}

function activeMap(rows) {
  return new Map(
    (Array.isArray(rows) ? rows : [])
      .filter((row) => row?.active !== false)
      .map((row) => [Number(row.id), row]),
  )
}

function hasCompleteItems(items) {
  return Array.isArray(items)
    && items.length >= 1
    && items.length <= 10
    && items.every(({ petId, serviceId }) => (
      Number.isInteger(petId)
      && petId > 0
      && Number.isInteger(serviceId)
      && serviceId > 0
    ))
}

export default function Confirmacion({ api = bookingApi }) {
  const navigate = useNavigate()
  const { draft, save } = useBookingDraft()
  const [resolvedItems, setResolvedItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)
  const draftRef = useRef(draft)
  const mountedRef = useRef(false)
  const generationRef = useRef(0)
  const submittingRef = useRef(false)

  useEffect(() => {
    draftRef.current = draft
  }, [draft])

  const loadNames = useCallback(async () => {
    const currentDraft = draftRef.current
    if (!hasCompleteItems(currentDraft.items)) {
      navigate('/reservar', { replace: true })
      return
    }
    if (!currentDraft.startsAt) {
      navigate('/calendario', { replace: true })
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
      const petMap = activeMap(pets)
      const serviceMap = activeMap(services)
      const current = currentDraft.items
        .filter(({ petId, serviceId }) => (
          petMap.has(petId) && serviceMap.has(serviceId)
        ))
        .map(({ petId, serviceId }) => ({
          petId,
          petName: petMap.get(petId).name,
          serviceId,
          serviceName: serviceMap.get(serviceId).name,
        }))

      if (current.length !== currentDraft.items.length) {
        save({ ...currentDraft, items: current, startsAt: '' })
        navigate('/reservar', { replace: true })
        return
      }
      setResolvedItems(current)
    } catch (error) {
      if (mountedRef.current && generationRef.current === generation) {
        setLoadError(safeMessage(error, LOAD_ERROR))
      }
    } finally {
      if (mountedRef.current && generationRef.current === generation) {
        setLoading(false)
      }
    }
  }, [api, navigate, save])

  useEffect(() => {
    mountedRef.current = true
    let cancelled = false
    queueMicrotask(() => {
      if (!cancelled) {
        void loadNames()
      }
    })
    return () => {
      cancelled = true
      mountedRef.current = false
      generationRef.current += 1
    }
  }, [loadNames])

  const handleConfirm = async () => {
    if (submittingRef.current || result) {
      return
    }
    submittingRef.current = true
    setSubmitting(true)
    setSubmitError('')
    const submittedDraft = draftRef.current

    try {
      const reservation = await api.createReservation(submittedDraft)
      clearPersistedBookingDraft()
      if (!mountedRef.current) {
        return
      }
      setResult(reservation)
    } catch (error) {
      if (error instanceof ApiError
        && error.status === 409
        && error.code === 'SLOT_ALREADY_BOOKED') {
        const message = safeMessage(
          error,
          'Ese horario acaba de ser reservado. Elige otro bloque disponible.',
        )
        clearPersistedBookingStart(
          submittedDraft.startsAt,
          submittedDraft,
        )
        if (!mountedRef.current) {
          return
        }
        navigate('/calendario', {
          replace: true,
          state: {
            collisionMessage: message,
            refreshAvailability: true,
          },
        })
        return
      }
      if (mountedRef.current) {
        setSubmitError(safeMessage(error, SUBMIT_ERROR))
      }
    } finally {
      submittingRef.current = false
      if (mountedRef.current) {
        setSubmitting(false)
      }
    }
  }

  const formatted = formatClinicDateTime(result?.startsAt ?? draft.startsAt)

  return (
    <>
      <Header />
      <AppointmentHero step={3} />
      <main className="main confirmacion-page">
        <div className="container">
          <div className="confirmacion-contenido">
            <section className="confirmacion-detalles" aria-labelledby="confirmation-title">
              <h2 className="confirmacion-subtitulo" id="confirmation-title">
                Revisa tu reserva
              </h2>
              <p className="confirmacion-descripcion">
                Confirma las mascotas, servicios y el horario antes de enviar la solicitud.
              </p>

              {loading && (
                <p className="booking-state" role="status" aria-live="polite">
                  Revisando los datos actuales…
                </p>
              )}

              {!loading && loadError && (
                <div className="booking-state booking-state--error">
                  <p role="alert">{loadError}</p>
                  <button type="button" className="btn btn-secundario" onClick={loadNames}>
                    Reintentar
                  </button>
                </div>
              )}

              {!loading && !loadError && resolvedItems.length > 0 && (
                <>
                  <article className="confirmacion-bloque">
                    <h3 className="confirmacion-bloque-titulo">
                      Mascotas y servicios
                    </h3>
                    <ul className="confirmation-items">
                      {resolvedItems.map((item) => (
                        <li className="confirmation-item" key={item.petId}>
                          <span className="confirmation-item__pet">{item.petName}</span>
                          <span className="confirmation-item__service">
                            {item.serviceName}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </article>

                  <article className="confirmacion-bloque">
                    <h3 className="confirmacion-bloque-titulo">Horario seleccionado</h3>
                    <div className="confirmacion-grid-datos confirmacion-grid-datos--dos">
                      <div className="dato-item">
                        <span className="dato-texto">
                          <span className="dato-etiqueta">Fecha</span>
                          <span className="dato-valor">{formatted.date}</span>
                        </span>
                      </div>
                      <div className="dato-item">
                        <span className="dato-texto">
                          <span className="dato-etiqueta">Hora</span>
                          <span className="dato-valor">{formatted.time}</span>
                        </span>
                      </div>
                    </div>
                  </article>

                  {draft.note && (
                    <article className="confirmacion-bloque">
                      <h3 className="confirmacion-bloque-titulo">Nota para la clínica</h3>
                      <p className="confirmation-note">{draft.note}</p>
                    </article>
                  )}

                  <div className="confirmacion-aviso">
                    <p className="aviso-texto">
                      Después de reservar, revisa el estado en tu panel y notificaciones.
                    </p>
                  </div>

                  {!result && (
                    <div className="confirmacion-acciones">
                      <button
                        type="button"
                        className="btn btn-secundario"
                        onClick={() => navigate('/calendario')}
                      >
                        ← Volver al horario
                      </button>
                      <button
                        type="button"
                        className="btn btn-primario"
                        disabled={submitting}
                        onClick={handleConfirm}
                      >
                        {submitting ? 'Registrando reserva…' : 'Confirmar reserva'}
                      </button>
                    </div>
                  )}

                  {submitError && (
                    <p className="booking-feedback booking-feedback--error" role="alert">
                      {submitError}
                    </p>
                  )}

                  {result && (
                    <div className="confirmation-success">
                      <p role="status" aria-live="polite">
                        {result.status === 'CONFIRMED'
                          ? 'Tu reserva quedó confirmada.'
                          : 'Recibimos tu solicitud y está pendiente de confirmación.'}
                      </p>
                      <Link className="btn btn-primario" to="/panel">
                        Ir a mi panel
                      </Link>
                    </div>
                  )}
                </>
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

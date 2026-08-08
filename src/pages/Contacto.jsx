import { useEffect, useRef, useState } from 'react'
import { contactApi, whatsappUrl } from '../api/contactApi'
import { ApiError } from '../api/http'
import Header from '../components/Header'
import Footer from '../components/Footer'
import HeartBadge from '../components/HeartBadge'

const initialValues = {
  correo: '',
  mensaje: '',
  nombre: '',
  website: '',
}
const initialErrors = {
  correo: '',
  mensaje: '',
  nombre: '',
  website: '',
}
const whatsappMessage = 'Hola AmiDog, quiero hacer una consulta.'
const invalidContactResponseMessage = 'No pudimos confirmar el envío. Intenta nuevamente.'
const invalidHoneypotMessage = 'No pudimos validar la consulta. Intenta nuevamente.'

function validateField(name, value) {
  if (name === 'nombre') {
    if (!value.trim()) return 'Escribe tu nombre.'
    if (value.trim().length < 2) return 'El nombre debe tener al menos 2 caracteres.'
    if (value.length > 120) return 'El nombre puede tener hasta 120 caracteres.'
  }

  if (name === 'correo') {
    if (!value.trim()) return 'Escribe tu correo electrónico.'
    if (value.length > 254) return 'El correo puede tener hasta 254 caracteres.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim())) return 'Escribe un correo válido.'
  }

  if (name === 'mensaje') {
    if (!value.trim()) return 'Escribe tu mensaje.'
    if (value.trim().length < 10) return 'El mensaje debe tener al menos 10 caracteres.'
    if (value.length > 2000) return 'El mensaje puede tener hasta 2000 caracteres.'
  }

  if (name === 'website' && value.length > 200) return invalidHoneypotMessage

  return ''
}

function safeContactError(error) {
  if (error instanceof ApiError
    && ['INVALID_CONTACT_RESPONSE', 'UNEXPECTED_RESPONSE_STATUS']
      .includes(error.code)) {
    return invalidContactResponseMessage
  }
  if (error instanceof ApiError
    && typeof error.message === 'string'
    && error.message.trim()) {
    return error.message
  }
  return 'No pudimos enviar tu consulta. Intenta nuevamente.'
}

export default function Contacto({
  api = contactApi,
  whatsappNumber = import.meta.env.VITE_WHATSAPP_NUMBER,
}) {
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState(initialErrors)
  const [feedback, setFeedback] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const nombreRef = useRef(null)
  const correoRef = useRef(null)
  const mensajeRef = useRef(null)
  const submittingRef = useRef(false)
  const whatsappHref = whatsappUrl(whatsappNumber, whatsappMessage)

  useEffect(() => {
    if (feedback?.type !== 'success') return
    const timer = window.setTimeout(() => setFeedback(null), 3600)
    return () => window.clearTimeout(timer)
  }, [feedback])

  const handleChange = (event) => {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
    if (errors[name]) {
      setErrors((current) => ({ ...current, [name]: validateField(name, value) }))
    }
    if (feedback?.type === 'error') {
      setFeedback(null)
    }
  }

  const handleBlur = (event) => {
    const { name, value } = event.target
    setErrors((current) => ({ ...current, [name]: validateField(name, value) }))
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    setFeedback(null)

    const nextErrors = Object.fromEntries(
      ['nombre', 'correo', 'mensaje', 'website']
        .map((name) => [name, validateField(name, values[name])]),
    )
    setErrors(nextErrors)
    const firstInvalid = Object.keys(nextErrors).find((name) => nextErrors[name])

    if (firstInvalid) {
      const invalidRef = {
        nombre: nombreRef,
        correo: correoRef,
        mensaje: mensajeRef,
      }[firstInvalid]
      if (invalidRef) {
        invalidRef.current?.focus()
      } else {
        setFeedback({
          message: invalidHoneypotMessage,
          type: 'error',
        })
      }
      return
    }

    submittingRef.current = true
    setSubmitting(true)

    try {
      const response = await api.send({
        name: values.nombre,
        email: values.correo,
        message: values.mensaje,
        website: values.website,
      })
      if (response?.status !== 202
        || typeof response.message !== 'string'
        || !response.message.trim()) {
        throw new ApiError(invalidContactResponseMessage, {
          code: 'INVALID_CONTACT_RESPONSE',
          status: Number.isInteger(response?.status) ? response.status : 0,
        })
      }
      setFeedback({ message: response.message.trim(), type: 'success' })
      setValues(initialValues)
      setErrors(initialErrors)
    } catch (requestError) {
      setFeedback({
        message: safeContactError(requestError),
        type: 'error',
      })
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <>
      <Header />
      <main className="contact-page">
        <section className="contact-hero" aria-labelledby="contact-title">
          <HeartBadge>HAZ TU CONSULTA</HeartBadge>
          <h1 className="contact-hero__title" id="contact-title">
            <img src="/assets/img/patitas.png" alt="" className="patita-izq" aria-hidden="true" />
            Contáctanos
            <img src="/assets/img/patitas.png" alt="" className="patita-der" aria-hidden="true" />
          </h1>
          <div className="contact-hero__layout">
            <form className="contact-form" id="contact-form" noValidate onSubmit={handleSubmit}>
              <div className="contact-form__group">
                <label htmlFor="nombre">Nombre</label>
                <input ref={nombreRef} id="nombre" name="nombre" type="text" autoComplete="name" placeholder="Tu nombre" required minLength="2" maxLength="120" value={values.nombre} onChange={handleChange} onBlur={handleBlur} aria-describedby="nombre-error" aria-invalid={Boolean(errors.nombre)} />
                <p className="contact-form__error" id="nombre-error" aria-live="polite">{errors.nombre}</p>
              </div>
              <div className="contact-form__group">
                <label htmlFor="correo">Correo electrónico</label>
                <input ref={correoRef} id="correo" name="correo" type="email" autoComplete="email" placeholder="nombre@correo.cl" required maxLength="254" value={values.correo} onChange={handleChange} onBlur={handleBlur} aria-describedby="correo-error" aria-invalid={Boolean(errors.correo)} />
                <p className="contact-form__error" id="correo-error" aria-live="polite">{errors.correo}</p>
              </div>
              <div className="contact-form__group">
                <label htmlFor="mensaje">Mensaje</label>
                <textarea ref={mensajeRef} id="mensaje" name="mensaje" placeholder="Escribe tu mensaje aquí..." required minLength="10" maxLength="2000" value={values.mensaje} onChange={handleChange} onBlur={handleBlur} aria-describedby="mensaje-error" aria-invalid={Boolean(errors.mensaje)}></textarea>
                <p className="contact-form__error" id="mensaje-error" aria-live="polite">{errors.mensaje}</p>
              </div>
              <div className="contact-form__honeypot" aria-hidden="true">
                <label htmlFor="website">Sitio web</label>
                <input
                  id="website"
                  name="website"
                  type="text"
                  autoComplete="off"
                  maxLength="200"
                  tabIndex={-1}
                  value={values.website}
                  onChange={handleChange}
                />
              </div>
              {feedback?.type === 'error' && (
                <p className="contact-form__feedback contact-form__feedback--error" role="alert">
                  {feedback.message}
                </p>
              )}
              <button className="contact-form__button" type="submit" disabled={submitting}>
                {submitting ? 'Enviando…' : 'Enviar por correo'}
              </button>
            </form>
            <div className="contact-list" aria-label="Datos de contacto">
              {whatsappHref && (
                <a className="contact-list__item contact-list__item--primary" href={whatsappHref} target="_blank" rel="noopener noreferrer"><span className="contact-list__icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12.1 2a9.8 9.8 0 0 0-8.4 14.9L2.4 22l5.2-1.3A9.9 9.9 0 1 0 12.1 2Zm0 17.8a7.8 7.8 0 0 1-4-1.1l-.3-.2-3.1.8.8-3-.2-.3a7.7 7.7 0 1 1 6.8 3.8Zm4.3-5.8c-.2-.1-1.4-.7-1.6-.8-.2-.1-.4-.1-.6.1l-.7.9c-.2.2-.3.2-.5.1a6.4 6.4 0 0 1-1.9-1.2 7 7 0 0 1-1.3-1.6c-.1-.2 0-.4.1-.5l.4-.5.2-.4c.1-.2 0-.4 0-.5l-.7-1.7c-.2-.4-.4-.4-.6-.4h-.5c-.2 0-.5.1-.8.4-.3.3-1 1-1 2.4s1 2.8 1.2 3c.1.2 2.1 3.3 5.2 4.5.7.3 1.3.5 1.7.6.7.2 1.4.2 1.9.1.6-.1 1.8-.7 2-1.4.3-.7.3-1.3.2-1.4-.1-.2-.3-.3-.5-.4Z" /></svg></span><span>Contactar por WhatsApp</span></a>
              )}
              {!whatsappHref && import.meta.env.DEV && (
                <p className="contact-list__warning" role="note">
                  Configura VITE_WHATSAPP_NUMBER para habilitar WhatsApp.
                </p>
              )}
              <a className="contact-list__item" href="https://www.google.com/maps/search/?api=1&query=Sta+Raquel+10815+La+Florida+Chile" target="_blank" rel="noopener noreferrer"><span className="contact-list__icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 2a7 7 0 0 0-7 7c0 5.4 7 13 7 13s7-7.6 7-13a7 7 0 0 0-7-7Zm0 10.1A3.1 3.1 0 1 1 12 6a3.1 3.1 0 0 1 0 6.1Z" /></svg></span><span className="contact-list__text">Sta Raquel 10815, 8310581 La Florida, Región Metropolitana</span></a>
              <a className="contact-list__item" href="https://www.facebook.com/search/top?q=amidog" target="_blank" rel="noopener noreferrer"><span className="contact-list__icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M14.3 8.5V6.7c0-.8.5-1 1-1h2.6V2.2L14.3 2c-3.5 0-4.5 2.2-4.5 4.4v2.1H7v4h2.8V22h4.5v-9.5h3.3l.5-4h-3.8Z" /></svg></span><span>Facebook</span></a>
              <a className="contact-list__item" href="https://www.instagram.com/amidog_vet" target="_blank" rel="noopener noreferrer"><span className="contact-list__icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M7.2 2h9.6A5.2 5.2 0 0 1 22 7.2v9.6a5.2 5.2 0 0 1-5.2 5.2H7.2A5.2 5.2 0 0 1 2 16.8V7.2A5.2 5.2 0 0 1 7.2 2Zm0 2A3.2 3.2 0 0 0 4 7.2v9.6A3.2 3.2 0 0 0 7.2 20h9.6a3.2 3.2 0 0 0 3.2-3.2V7.2A3.2 3.2 0 0 0 16.8 4H7.2Zm10.1 1.5a1.2 1.2 0 1 1 0 2.4 1.2 1.2 0 0 1 0-2.4ZM12 7a5 5 0 1 1 0 10 5 5 0 0 1 0-10Zm0 2a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z" /></svg></span><span>Instagram</span></a>
            </div>
          </div>
          <img src="/assets/img/estrella.png" alt="" className="decor-star--top" aria-hidden="true" />
          <img src="/assets/img/estrella.png" alt="" className="decor-star--bottom" aria-hidden="true" />
          <img className="contact-cat" src="/assets/img/GatitoAsomado.png" alt="Gato asomándose desde el costado derecho" />
        </section>
      </main>
      <Footer />
      <div className={`toast${feedback?.type === 'success' ? ' is-visible' : ''}`} id="toast" role="status" aria-live="polite">
        {feedback?.type === 'success' ? feedback.message : ''}
      </div>
    </>
  )
}

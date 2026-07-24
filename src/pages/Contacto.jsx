import { useEffect, useRef, useState } from 'react'
import Header from '../components/Header'
import Footer from '../components/Footer'
import HeartBadge from '../components/HeartBadge'

const initialValues = { nombre: '', correo: '', mensaje: '' }
const initialErrors = { nombre: '', correo: '', mensaje: '' }

function validateField(name, value) {
  if (name === 'nombre') {
    if (!value.trim()) return 'Escribe tu nombre.'
    if (value.trim().length < 2) return 'El nombre debe tener al menos 2 caracteres.'
  }

  if (name === 'correo') {
    if (!value.trim()) return 'Escribe tu correo electrónico.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim())) return 'Escribe un correo válido.'
  }

  if (name === 'mensaje') {
    if (!value.trim()) return 'Escribe tu mensaje.'
    if (value.trim().length < 10) return 'El mensaje debe tener al menos 10 caracteres.'
  }

  return ''
}

export default function Contacto() {
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState(initialErrors)
  const [toast, setToast] = useState('')
  const refs = { nombre: useRef(null), correo: useRef(null), mensaje: useRef(null) }

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(''), 3600)
    return () => window.clearTimeout(timer)
  }, [toast])

  const handleChange = (event) => {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
    if (errors[name]) {
      setErrors((current) => ({ ...current, [name]: validateField(name, value) }))
    }
  }

  const handleBlur = (event) => {
    const { name, value } = event.target
    setErrors((current) => ({ ...current, [name]: validateField(name, value) }))
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    const nextErrors = Object.fromEntries(Object.entries(values).map(([name, value]) => [name, validateField(name, value)]))
    setErrors(nextErrors)
    const firstInvalid = Object.keys(nextErrors).find((name) => nextErrors[name])

    if (firstInvalid) {
      refs[firstInvalid].current?.focus()
      return
    }

    setToast('Tu consulta fue registrada correctamente. Te contactaremos pronto.')
    setValues(initialValues)
    setErrors(initialErrors)
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
                <input ref={refs.nombre} id="nombre" name="nombre" type="text" autoComplete="name" placeholder="Tu nombre" required minLength="2" value={values.nombre} onChange={handleChange} onBlur={handleBlur} aria-invalid={Boolean(errors.nombre)} />
                <p className="contact-form__error" id="nombre-error" aria-live="polite">{errors.nombre}</p>
              </div>
              <div className="contact-form__group">
                <label htmlFor="correo">Correo electrónico</label>
                <input ref={refs.correo} id="correo" name="correo" type="email" autoComplete="email" placeholder="nombre@correo.cl" required value={values.correo} onChange={handleChange} onBlur={handleBlur} aria-invalid={Boolean(errors.correo)} />
                <p className="contact-form__error" id="correo-error" aria-live="polite">{errors.correo}</p>
              </div>
              <div className="contact-form__group">
                <label htmlFor="mensaje">Mensaje</label>
                <textarea ref={refs.mensaje} id="mensaje" name="mensaje" placeholder="Escribe tu mensaje aquí..." required minLength="10" value={values.mensaje} onChange={handleChange} onBlur={handleBlur} aria-invalid={Boolean(errors.mensaje)}></textarea>
                <p className="contact-form__error" id="mensaje-error" aria-live="polite">{errors.mensaje}</p>
              </div>
              <button className="contact-form__button" type="submit">Enviar</button>
            </form>
            <div className="contact-list" aria-label="Datos de contacto">
              <a className="contact-list__item" href="https://www.google.com/maps/search/?api=1&query=Sta+Raquel+10815+La+Florida+Chile" target="_blank" rel="noopener noreferrer"><span className="contact-list__icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 2a7 7 0 0 0-7 7c0 5.4 7 13 7 13s7-7.6 7-13a7 7 0 0 0-7-7Zm0 10.1A3.1 3.1 0 1 1 12 6a3.1 3.1 0 0 1 0 6.1Z" /></svg></span><span className="contact-list__text">Sta Raquel 10815, 8310581 La Florida, Región Metropolitana</span></a>
              <a className="contact-list__item" href="https://wa.me/?text=Hola%20AmiDog%2C%20quiero%20hacer%20una%20consulta." target="_blank" rel="noopener noreferrer"><span className="contact-list__icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12.1 2a9.8 9.8 0 0 0-8.4 14.9L2.4 22l5.2-1.3A9.9 9.9 0 1 0 12.1 2Zm0 17.8a7.8 7.8 0 0 1-4-1.1l-.3-.2-3.1.8.8-3-.2-.3a7.7 7.7 0 1 1 6.8 3.8Zm4.3-5.8c-.2-.1-1.4-.7-1.6-.8-.2-.1-.4-.1-.6.1l-.7.9c-.2.2-.3.2-.5.1a6.4 6.4 0 0 1-1.9-1.2 7 7 0 0 1-1.3-1.6c-.1-.2 0-.4.1-.5l.4-.5.2-.4c.1-.2 0-.4 0-.5l-.7-1.7c-.2-.4-.4-.4-.6-.4h-.5c-.2 0-.5.1-.8.4-.3.3-1 1-1 2.4s1 2.8 1.2 3c.1.2 2.1 3.3 5.2 4.5.7.3 1.3.5 1.7.6.7.2 1.4.2 1.9.1.6-.1 1.8-.7 2-1.4.3-.7.3-1.3.2-1.4-.1-.2-.3-.3-.5-.4Z" /></svg></span><span>Whatsapp</span></a>
              <a className="contact-list__item" href="https://www.facebook.com/search/top?q=amidog" target="_blank" rel="noopener noreferrer"><span className="contact-list__icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M14.3 8.5V6.7c0-.8.5-1 1-1h2.6V2.2L14.3 2c-3.5 0-4.5 2.2-4.5 4.4v2.1H7v4h2.8V22h4.5v-9.5h3.3l.5-4h-3.8Z" /></svg></span><span>Facebook</span></a>
              <a className="contact-list__item" href="https://www.instagram.com/amidog/" target="_blank" rel="noopener noreferrer"><span className="contact-list__icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M7.2 2h9.6A5.2 5.2 0 0 1 22 7.2v9.6a5.2 5.2 0 0 1-5.2 5.2H7.2A5.2 5.2 0 0 1 2 16.8V7.2A5.2 5.2 0 0 1 7.2 2Zm0 2A3.2 3.2 0 0 0 4 7.2v9.6A3.2 3.2 0 0 0 7.2 20h9.6a3.2 3.2 0 0 0 3.2-3.2V7.2A3.2 3.2 0 0 0 16.8 4H7.2Zm10.1 1.5a1.2 1.2 0 1 1 0 2.4 1.2 1.2 0 0 1 0-2.4ZM12 7a5 5 0 1 1 0 10 5 5 0 0 1 0-10Zm0 2a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z" /></svg></span><span>Instagram</span></a>
            </div>
          </div>
          <img src="/assets/img/estrella.png" alt="" className="decor-star--top" aria-hidden="true" />
          <img src="/assets/img/estrella.png" alt="" className="decor-star--bottom" aria-hidden="true" />
          <img className="contact-cat" src="/assets/img/GatitoAsomado.png" alt="Gato asomándose desde el costado derecho" />
        </section>
      </main>
      <Footer />
      <div className={`toast${toast ? ' is-visible' : ''}`} id="toast" role="status" aria-live="polite">{toast}</div>
    </>
  )
}

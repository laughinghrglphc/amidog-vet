import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'
import { CloseCircleIcon } from './Icons'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function Modal({ title, children, onClose, wide = false }) {
  const closeButtonRef = useRef(null)
  const dialogRef = useRef(null)
  const headingRef = useRef(null)
  const scopeRef = useRef(null)
  const initialTitleRef = useRef(title)
  const titleId = useId()

  useEffect(() => {
    const previousFocus = document.activeElement
    const isolated = [...document.body.children]
      .filter((element) => element !== scopeRef.current)
      .map((element) => ({
        ariaHidden: element.getAttribute('aria-hidden'),
        element,
        inert: element.hasAttribute('inert'),
      }))
    const handleKey = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab') return
      const focusable = [
        ...dialogRef.current.querySelectorAll(FOCUSABLE_SELECTOR),
      ]
      const first = focusable[0]
      const last = focusable.at(-1)
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last?.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first?.focus()
      }
    }
    isolated.forEach(({ element }) => {
      element.setAttribute('inert', '')
      element.setAttribute('aria-hidden', 'true')
    })
    document.body.classList.add('modal-open')
    window.addEventListener('keydown', handleKey)
    closeButtonRef.current?.focus()
    return () => {
      document.body.classList.remove('modal-open')
      window.removeEventListener('keydown', handleKey)
      isolated.forEach(({ ariaHidden, element, inert }) => {
        if (!inert) element.removeAttribute('inert')
        if (ariaHidden === null) element.removeAttribute('aria-hidden')
        else element.setAttribute('aria-hidden', ariaHidden)
      })
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus()
      }
    }
  }, [onClose])

  useEffect(() => {
    if (initialTitleRef.current !== title) {
      initialTitleRef.current = title
      headingRef.current?.focus()
    }
  }, [title])

  return createPortal(
    <div
      ref={scopeRef}
      className="panel-page client-modal-scope"
      role="presentation"
    >
      <div
        className="modal-backdrop"
        role="presentation"
        onMouseDown={(event) => {
          if (event.target === event.currentTarget) onClose()
        }}
      >
        <section ref={dialogRef} className={`modal ${wide ? 'modal--wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby={titleId}>
          <div className="modal__header">
            <h2 ref={headingRef} id={titleId} tabIndex="-1">{title}</h2>
            <button ref={closeButtonRef} type="button" aria-label="Cerrar" onClick={onClose}><CloseCircleIcon /></button>
          </div>
          <div className="modal__body">{children}</div>
        </section>
      </div>
    </div>,
    document.body,
  )
}

export default Modal

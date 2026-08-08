import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

export default function Modal({ children, onClose, title, wide = false }) {
  const closeButtonRef = useRef(null)
  const dialogRef = useRef(null)
  const titleId = useId()

  useEffect(() => {
    const previousFocus = document.activeElement
    const appRoot = document.getElementById('root')
    const previousOverflow = document.body.style.overflow
    const rootWasInert = appRoot?.hasAttribute('inert')

    appRoot?.setAttribute('inert', '')
    document.body.style.overflow = 'hidden'
    closeButtonRef.current?.focus()

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab') return

      const focusableElements = [
        ...dialogRef.current.querySelectorAll(FOCUSABLE_SELECTOR),
      ]
      const firstElement = focusableElements[0]
      const lastElement = focusableElements.at(-1)

      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault()
        lastElement?.focus()
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault()
        firstElement?.focus()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      if (!rootWasInert) appRoot?.removeAttribute('inert')
      previousFocus?.focus()
    }
  }, [onClose])

  useEffect(() => {
    closeButtonRef.current?.focus()
  }, [title])

  return createPortal(
    <div
      className="admin-modal-backdrop admin-modal-scope"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <section
        ref={dialogRef}
        aria-labelledby={titleId}
        aria-modal="true"
        className={`admin-modal ${wide ? 'admin-modal--wide' : ''}`}
        role="dialog"
      >
        <header>
          <h2 id={titleId}>{title}</h2>
          <button
            ref={closeButtonRef}
            type="button"
            aria-label="Cerrar"
            onClick={onClose}
          >
            <span aria-hidden="true">×</span>
          </button>
        </header>
        <div className="admin-modal__content">{children}</div>
      </section>
    </div>,
    document.body,
  )
}

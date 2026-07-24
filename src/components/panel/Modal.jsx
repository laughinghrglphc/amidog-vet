import { useEffect } from 'react'
import { CloseCircleIcon } from './Icons'

function Modal({ title, children, onClose, wide = false }) {
  useEffect(() => {
    const handleKey = (event) => {
      if (event.key === 'Escape') onClose()
    }
    document.body.classList.add('modal-open')
    document.addEventListener('keydown', handleKey)
    return () => {
      document.body.classList.remove('modal-open')
      document.removeEventListener('keydown', handleKey)
    }
  }, [onClose])

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section className={`modal ${wide ? 'modal--wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby="modal-title" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal__header">
          <h2 id="modal-title">{title}</h2>
          <button type="button" aria-label="Cerrar" onClick={onClose}><CloseCircleIcon /></button>
        </div>
        <div className="modal__body">{children}</div>
      </section>
    </div>
  )
}

export default Modal

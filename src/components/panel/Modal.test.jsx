import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import Modal from './Modal'

function ModalHarness({ removeTrigger = false }) {
  const [open, setOpen] = useState(false)
  const [step, setStep] = useState('list')

  return (
    <div data-testid="background">
      {!removeTrigger || !open ? (
        <button type="button" onClick={() => setOpen(true)}>Abrir panel</button>
      ) : null}
      {open && (
        <Modal
          title={step === 'list' ? 'Mis mascotas' : 'Editar a Milo'}
          onClose={() => setOpen(false)}
        >
          {step === 'list' ? (
            <button type="button" onClick={() => setStep('edit')}>Editar Milo</button>
          ) : (
            <button type="button">Guardar mascota</button>
          )}
        </Modal>
      )}
    </div>
  )
}

describe('client Modal', () => {
  it('portals to the body, isolates the background, and contains forward and reverse Tab', async () => {
    const user = userEvent.setup()
    const view = render(<ModalHarness />)
    await user.click(screen.getByRole('button', { name: 'Abrir panel' }))

    const dialog = screen.getByRole('dialog', { name: 'Mis mascotas' })
    expect(view.container).not.toContainElement(dialog)
    expect(view.container).toHaveAttribute('inert')
    expect(view.container).toHaveAttribute('aria-hidden', 'true')

    const close = screen.getByRole('button', { name: 'Cerrar' })
    const last = screen.getByRole('button', { name: 'Editar Milo' })
    last.focus()
    await user.tab()
    expect(close).toHaveFocus()
    await user.tab({ shift: true })
    expect(last).toHaveFocus()
  })

  it('closes with Escape and restores focus only to a connected trigger', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const trigger = document.createElement('button')
    trigger.textContent = 'Abrir'
    document.body.append(trigger)
    trigger.focus()
    const view = render(
      <Modal title="Mi perfil" onClose={onClose}>
        <button type="button">Guardar</button>
      </Modal>,
    )

    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)
    trigger.remove()
    expect(() => view.unmount()).not.toThrow()
    expect(document.activeElement).not.toBe(trigger)
  })

  it('moves focus deliberately when chained dialog content replaces the trigger', async () => {
    const user = userEvent.setup()
    render(<ModalHarness removeTrigger />)
    await user.click(screen.getByRole('button', { name: 'Abrir panel' }))
    await user.click(screen.getByRole('button', { name: 'Editar Milo' }))

    const heading = screen.getByRole('heading', { name: 'Editar a Milo' })
    expect(heading).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { resolve } from 'node:path'
import { cwd } from 'node:process'
import { compile } from 'sass'
import Modal from './Modal'

let adminStyleElement

beforeAll(() => {
  const adminStyles = compile(
    resolve(cwd(), 'src/styles/_panel-administrador.scss'),
  ).css
  adminStyleElement = document.createElement('style')
  adminStyleElement.textContent = adminStyles
  document.head.append(adminStyleElement)
})

afterAll(() => {
  adminStyleElement?.remove()
})

describe('Modal', () => {
  it('moves focus into the dialog, closes with Escape, and restores focus', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const trigger = document.createElement('button')
    trigger.textContent = 'Abrir'
    document.body.append(trigger)
    trigger.focus()

    const { unmount } = render(
      <Modal title="Detalle de la reserva" onClose={onClose}>
        <button type="button">Acción</button>
      </Modal>,
    )

    expect(screen.getByRole('dialog', { name: 'Detalle de la reserva' })).toContainElement(document.activeElement)

    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)

    unmount()
    expect(trigger).toHaveFocus()
    trigger.remove()
  })

  it('keeps Tab focus inside the open dialog', async () => {
    const user = userEvent.setup()
    render(
      <Modal title="Mi perfil" onClose={() => undefined}>
        <button type="button">Primera acción</button>
        <button type="button">Última acción</button>
      </Modal>,
    )

    const close = screen.getByRole('button', { name: 'Cerrar' })
    const last = screen.getByRole('button', { name: 'Última acción' })
    last.focus()
    await user.tab()
    expect(close).toHaveFocus()
  })

  it('carries the admin theme and focus scope into its body portal', () => {
    render(
      <Modal title="Mi perfil" onClose={() => undefined}>
        <button type="button">Acción</button>
      </Modal>,
    )

    const dialog = screen.getByRole('dialog', { name: 'Mi perfil' })
    const portalScope = dialog.closest('.admin-modal-scope')
    const close = screen.getByRole('button', { name: 'Cerrar' })

    expect(portalScope).toBeInTheDocument()
    expect(getComputedStyle(portalScope).getPropertyValue('--admin-teal').trim()).toBe('#16ad9f')
    expect(close).toHaveFocus()

    const focusSelectors = [...adminStyleElement.sheet.cssRules]
      .map((rule) => rule.selectorText ?? '')
      .filter((selector) => selector.includes(':focus-visible'))
      .join(', ')
    expect(focusSelectors).toContain('.admin-modal-scope')
  })

  it('keeps reduced-motion rules scoped to portaled admin content', () => {
    const reducedMotionRule = [...adminStyleElement.sheet.cssRules]
      .find((rule) => rule.cssText.includes('prefers-reduced-motion'))
    expect(reducedMotionRule).toBeDefined()
    const reducedMotionSelectors = new Set(
      [...reducedMotionRule.cssRules]
        .flatMap((rule) => (rule.selectorText ?? '').split(','))
        .map((selector) => selector.trim())
        .filter(Boolean),
    )

    expect([...reducedMotionSelectors]).toEqual(expect.arrayContaining([
      '.admin-modal-scope',
      '.admin-modal-scope::before',
      '.admin-modal-scope::after',
      '.admin-modal-scope *',
      '.admin-modal-scope *::before',
      '.admin-modal-scope *::after',
    ]))
    expect([...reducedMotionSelectors]).not.toContain('*')
    expect([...reducedMotionSelectors]).not.toContain('*::before')
    expect([...reducedMotionSelectors]).not.toContain('*::after')
  })
})

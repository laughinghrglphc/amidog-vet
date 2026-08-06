import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { fakeAuth } from '../test/fakes'
import { renderApp } from '../test/renderApp'
import Register from './Register'

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

async function fillRegistration({
  confirmation = 'una-clave-segura-2026',
  password = 'una-clave-segura-2026',
} = {}) {
  await userEvent.type(screen.getByLabelText(/nombre completo/i), 'Ana Pérez')
  await userEvent.type(screen.getByLabelText(/teléfono/i), '+56912345678')
  await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'ana@example.cl')
  fireEvent.change(screen.getByLabelText(/^contraseña$/i), {
    target: { value: password },
  })
  fireEvent.change(screen.getByLabelText(/confirmar contraseña/i), {
    target: { value: confirmation },
  })
}

describe('Register', () => {
  it('sends exactly the backend fields and never sends confirmation', async () => {
    const api = fakeAuth()
    renderApp(<Register api={api} />)

    await fillRegistration()
    await userEvent.click(screen.getByRole('button', { name: /crear cuenta/i }))

    expect(api.register).toHaveBeenCalledWith({
      email: 'ana@example.cl',
      password: 'una-clave-segura-2026',
      name: 'Ana Pérez',
      phone: '+56912345678',
    })
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Revisa tu correo para verificar tu cuenta.',
    )
  })

  it.each([
    ['corta', 'corta', /entre 12 y 128/i],
    ['a'.repeat(129), 'a'.repeat(129), /entre 12 y 128/i],
    ['una-clave-segura-2026', 'otra-clave-segura', /no coinciden/i],
  ])('rejects invalid password pair before calling the server', async (
    password,
    confirmation,
    expected,
  ) => {
    const api = fakeAuth()
    renderApp(<Register api={api} />)

    await fillRegistration({ confirmation, password })
    await userEvent.click(screen.getByRole('button', { name: /crear cuenta/i }))

    expect(screen.getByRole('alert')).toHaveTextContent(expected)
    expect(api.register).not.toHaveBeenCalled()
  })

  it('prevents duplicate registration while the first request is pending', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.register.mockReturnValueOnce(pending.promise)
    renderApp(<Register api={api} />)

    await fillRegistration()
    const button = screen.getByRole('button', { name: /crear cuenta/i })
    await userEvent.dblClick(button)

    expect(api.register).toHaveBeenCalledTimes(1)
    expect(button).toBeDisabled()
    pending.resolve({ message: 'Revisa tu correo para verificar tu cuenta.' })
    expect(await screen.findByRole('status')).toBeVisible()
  })

  it('keeps a delayed successful registration successful and clears the form', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.register.mockReturnValueOnce(pending.promise)
    renderApp(<Register api={api} />)

    await fillRegistration()
    await userEvent.click(screen.getByRole('button', { name: /crear cuenta/i }))

    pending.resolve({ message: 'Revisa tu correo para verificar tu cuenta.' })

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Revisa tu correo para verificar tu cuenta.',
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/nombre completo/i)).toHaveValue('')
    expect(screen.getByLabelText(/teléfono/i)).toHaveValue('')
    expect(screen.getByLabelText(/correo electrónico/i)).toHaveValue('')
  })

  it('renders reviewed server field errors in an accessible alert', async () => {
    const api = fakeAuth()
    api.register.mockRejectedValueOnce(new ApiError(
      'La solicitud contiene datos inválidos.',
      {
        code: 'VALIDATION_ERROR',
        errors: {
          email: 'debe ser una dirección de correo válida',
          phone: 'el tamaño debe ser entre 1 y 30',
        },
        status: 400,
      },
    ))
    renderApp(<Register api={api} />)

    await fillRegistration()
    await userEvent.click(screen.getByRole('button', { name: /crear cuenta/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('La solicitud contiene datos inválidos.')
    expect(alert).toHaveTextContent('debe ser una dirección de correo válida')
    expect(alert).toHaveTextContent('el tamaño debe ser entre 1 y 30')
  })

  it('does not persist registration credentials', async () => {
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    renderApp(<Register api={fakeAuth()} />)

    await fillRegistration()
    await userEvent.click(screen.getByRole('button', { name: /crear cuenta/i }))

    expect(storageWrite).not.toHaveBeenCalled()
  })
})

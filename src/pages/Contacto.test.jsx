import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, resetCsrf } from '../api/http'
import { renderApp } from '../test/renderApp'
import Contacto from './Contacto'

const validInquiry = {
  email: 'ana@example.com',
  message: 'Necesito consultar por una vacuna.',
  name: 'Ana Pérez',
  website: '',
}

function fakeContactApi() {
  return {
    send: vi.fn().mockResolvedValue({
      message: 'Tu consulta fue enviada.',
      status: 202,
    }),
  }
}

async function fillInquiry() {
  await userEvent.type(screen.getByLabelText('Nombre'), validInquiry.name)
  await userEvent.type(
    screen.getByLabelText(/correo electrónico/i),
    validInquiry.email,
  )
  await userEvent.type(screen.getByLabelText('Mensaje'), validInquiry.message)
}

function setInquiry(overrides = {}) {
  const inquiry = { ...validInquiry, ...overrides }
  fireEvent.change(screen.getByLabelText('Nombre'), {
    target: { value: inquiry.name },
  })
  fireEvent.change(screen.getByLabelText(/correo electrónico/i), {
    target: { value: inquiry.email },
  })
  fireEvent.change(screen.getByLabelText('Mensaje'), {
    target: { value: inquiry.message },
  })
  fireEvent.change(document.querySelector('input[name="website"]'), {
    target: { value: inquiry.website },
  })
  return inquiry
}

function expectInquiryValues(inquiry) {
  expect(screen.getByLabelText('Nombre')).toHaveValue(inquiry.name)
  expect(screen.getByLabelText(/correo electrónico/i)).toHaveValue(
    inquiry.email,
  )
  expect(screen.getByLabelText('Mensaje')).toHaveValue(inquiry.message)
  expect(document.querySelector('input[name="website"]')).toHaveValue(
    inquiry.website,
  )
}

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function jsonResponse(status, payload) {
  return {
    json: vi.fn().mockResolvedValue(payload),
    ok: status >= 200 && status < 300,
    status,
  }
}

describe('Contacto', () => {
  beforeEach(() => {
    resetCsrf()
  })

  it('uses the shared cookie and CSRF-aware request path by default', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(200, {
        headerName: 'X-XSRF-TOKEN',
        token: 'contact-csrf-token',
      }))
      .mockResolvedValueOnce(jsonResponse(202, {
        message: 'Tu consulta fue enviada.',
      }))
    renderApp(<Contacto />)

    await fillInquiry()
    await userEvent.click(
      screen.getByRole('button', { name: /enviar por correo/i }),
    )

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Tu consulta fue enviada.',
    )
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/v1/contact', {
      body: expect.any(String),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'contact-csrf-token',
      },
      method: 'POST',
    })
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual(validInquiry)
  })

  it('submits a valid email inquiry to the backend and clears it after success', async () => {
    const api = fakeContactApi()
    renderApp(<Contacto api={api} />)

    await fillInquiry()
    await userEvent.click(
      screen.getByRole('button', { name: /enviar por correo/i }),
    )

    expect(api.send).toHaveBeenCalledWith(validInquiry)
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Tu consulta fue enviada.',
    )
    expect(screen.getByLabelText('Nombre')).toHaveValue('')
    expect(screen.getByLabelText(/correo electrónico/i)).toHaveValue('')
    expect(screen.getByLabelText('Mensaje')).toHaveValue('')
  })

  it.each([200, 201])(
    'rejects fulfilled status %i with a closed error and preserves every field',
    async (status) => {
      fetch
        .mockResolvedValueOnce(jsonResponse(200, {
          headerName: 'X-XSRF-TOKEN',
          token: 'contact-csrf-token',
        }))
        .mockResolvedValueOnce(jsonResponse(status, {
          message: 'Tu consulta fue enviada.',
        }))
      renderApp(<Contacto />)
      const inquiry = setInquiry({ website: 'bot-marker' })

      await userEvent.click(
        screen.getByRole('button', { name: /enviar por correo/i }),
      )

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'No pudimos confirmar el envío. Intenta nuevamente.',
      )
      expectInquiryValues(inquiry)
      expect(screen.getByRole('status')).not.toHaveTextContent(
        'Tu consulta fue enviada.',
      )
    },
  )

  it.each([
    {},
    { message: '' },
    { message: '   ' },
  ])(
    'rejects malformed 202 payload %# with a closed error and preserves every field',
    async (payload) => {
      fetch
        .mockResolvedValueOnce(jsonResponse(200, {
          headerName: 'X-XSRF-TOKEN',
          token: 'contact-csrf-token',
        }))
        .mockResolvedValueOnce(jsonResponse(202, payload))
      renderApp(<Contacto />)
      const inquiry = setInquiry({ website: 'bot-marker' })

      await userEvent.click(
        screen.getByRole('button', { name: /enviar por correo/i }),
      )

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'No pudimos confirmar el envío. Intenta nuevamente.',
      )
      expectInquiryValues(inquiry)
    },
  )

  it.each([
    { message: 'Tu consulta fue enviada.', status: 200 },
    { message: '   ', status: 202 },
  ])(
    'defensively rejects an invalid resolved API result %# and preserves every field',
    async (response) => {
      const api = fakeContactApi()
      api.send.mockResolvedValueOnce(response)
      renderApp(<Contacto api={api} />)
      const inquiry = setInquiry({ website: 'bot-marker' })

      await userEvent.click(
        screen.getByRole('button', { name: /enviar por correo/i }),
      )

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'No pudimos confirmar el envío. Intenta nuevamente.',
      )
      expectInquiryValues(inquiry)
    },
  )

  it('creates a direct clinic WhatsApp URL with an editable prefilled message', () => {
    renderApp(
      <Contacto api={fakeContactApi()} whatsappNumber="+56 9 1234-5678" />,
    )

    expect(
      screen.getByRole('link', { name: /contactar por whatsapp/i }),
    ).toHaveAttribute(
      'href',
      'https://wa.me/56912345678?text=Hola%20AmiDog%2C%20quiero%20hacer%20una%20consulta.',
    )
  })

  it('keeps the honeypot submitted while removing it from keyboard navigation', async () => {
    const api = fakeContactApi()
    renderApp(<Contacto api={api} />)
    const honeypot = document.querySelector('input[name="website"]')

    expect(honeypot).toHaveAttribute('type', 'text')
    expect(honeypot).toHaveAttribute('tabindex', '-1')
    expect(honeypot).toHaveAttribute('autocomplete', 'off')

    fireEvent.change(honeypot, { target: { value: 'https://spam.invalid' } })
    await fillInquiry()
    await userEvent.click(
      screen.getByRole('button', { name: /enviar por correo/i }),
    )

    expect(api.send).toHaveBeenCalledWith({
      ...validInquiry,
      website: 'https://spam.invalid',
    })
  })

  it('keeps user values and announces controlled provider and rate-limit errors', async () => {
    const api = fakeContactApi()
    api.send
      .mockRejectedValueOnce(new ApiError(
        'No pudimos enviar tu consulta. Inténtalo nuevamente o usa WhatsApp.',
        { code: 'EMAIL_DELIVERY_UNAVAILABLE', status: 503 },
      ))
      .mockRejectedValueOnce(new ApiError(
        'Demasiadas solicitudes. Intenta nuevamente más tarde.',
        { code: 'RATE_LIMITED', status: 429 },
      ))
    renderApp(
      <Contacto api={api} whatsappNumber="56912345678" />,
    )
    await fillInquiry()
    const submit = screen.getByRole(
      'button',
      { name: /enviar por correo/i },
    )

    await userEvent.click(submit)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos enviar tu consulta. Inténtalo nuevamente o usa WhatsApp.',
    )
    expect(screen.getByLabelText('Nombre')).toHaveValue(validInquiry.name)
    expect(screen.getByLabelText(/correo electrónico/i)).toHaveValue(
      validInquiry.email,
    )
    expect(screen.getByLabelText('Mensaje')).toHaveValue(validInquiry.message)
    expect(submit).toBeEnabled()

    await userEvent.click(submit)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Demasiadas solicitudes. Intenta nuevamente más tarde.',
    )
    expect(screen.getByLabelText('Nombre')).toHaveValue(validInquiry.name)
  })

  it('retires a stale request error when the inquiry is edited', async () => {
    const api = fakeContactApi()
    api.send.mockRejectedValueOnce(new ApiError(
      'No pudimos enviar tu consulta. Inténtalo nuevamente o usa WhatsApp.',
      { code: 'EMAIL_DELIVERY_UNAVAILABLE', status: 503 },
    ))
    renderApp(<Contacto api={api} />)
    await fillInquiry()
    await userEvent.click(
      screen.getByRole('button', { name: /enviar por correo/i }),
    )
    expect(await screen.findByRole('alert')).toBeVisible()

    await userEvent.type(screen.getByLabelText('Mensaje'), ' Más detalles.')

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('retires prior success before a locally invalid resubmission', async () => {
    renderApp(<Contacto api={fakeContactApi()} />)
    await fillInquiry()
    await userEvent.click(
      screen.getByRole('button', { name: /enviar por correo/i }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Tu consulta fue enviada.',
    )

    await userEvent.click(
      screen.getByRole('button', { name: /enviar por correo/i }),
    )

    expect(screen.getByText('Escribe tu nombre.')).toBeVisible()
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('prevents duplicate email submissions while the request is pending', async () => {
    const pending = deferred()
    const api = fakeContactApi()
    api.send.mockReturnValueOnce(pending.promise)
    renderApp(<Contacto api={api} />)
    await fillInquiry()
    const submit = screen.getByRole(
      'button',
      { name: /enviar por correo/i },
    )

    await userEvent.dblClick(submit)

    expect(api.send).toHaveBeenCalledTimes(1)
    expect(submit).toBeDisabled()
    pending.resolve({
      message: 'Tu consulta fue enviada.',
      status: 202,
    })
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Tu consulta fue enviada.',
    )
  })

  it('validates locally and focuses the first invalid field', async () => {
    const api = fakeContactApi()
    renderApp(<Contacto api={api} />)

    await userEvent.click(
      screen.getByRole('button', { name: /enviar por correo/i }),
    )

    expect(screen.getByLabelText('Nombre')).toHaveFocus()
    expect(screen.getByText('Escribe tu nombre.')).toBeVisible()
    expect(api.send).not.toHaveBeenCalled()
  })

  it.each([
    {
      field: 'name',
      label: 'Nombre',
      message: 'El nombre puede tener hasta 120 caracteres.',
      value: 'N'.repeat(121),
    },
    {
      field: 'email',
      label: /correo electrónico/i,
      message: 'El correo puede tener hasta 254 caracteres.',
      value: `${'a'.repeat(243)}@example.com`,
    },
    {
      field: 'message',
      label: 'Mensaje',
      message: 'El mensaje puede tener hasta 2000 caracteres.',
      value: 'M'.repeat(2001),
    },
  ])(
    'rejects a programmatically populated over-limit $field inline',
    async ({ field, label, message, value }) => {
      const api = fakeContactApi()
      renderApp(<Contacto api={api} />)
      setInquiry({ [field]: value })
      const control = screen.getByLabelText(label)

      await userEvent.click(
        screen.getByRole('button', { name: /enviar por correo/i }),
      )

      expect(control).toHaveFocus()
      expect(screen.getByText(message)).toBeVisible()
      expect(api.send).not.toHaveBeenCalled()
    },
  )

  it('rejects an over-limit programmatic honeypot with a closed accessible error', async () => {
    const api = fakeContactApi()
    renderApp(<Contacto api={api} />)
    const inquiry = setInquiry({ website: 'w'.repeat(201) })

    await userEvent.click(
      screen.getByRole('button', { name: /enviar por correo/i }),
    )

    expect(screen.getByRole('alert')).toHaveTextContent(
      'No pudimos validar la consulta. Intenta nuevamente.',
    )
    expectInquiryValues(inquiry)
    expect(api.send).not.toHaveBeenCalled()
  })

  it('accepts programmatically populated values at every declared maximum', async () => {
    const api = fakeContactApi()
    renderApp(<Contacto api={api} />)
    const inquiry = setInquiry({
      email: `${'a'.repeat(242)}@example.com`,
      message: 'M'.repeat(2000),
      name: 'N'.repeat(120),
      website: 'w'.repeat(200),
    })

    await userEvent.click(
      screen.getByRole('button', { name: /enviar por correo/i }),
    )

    expect(api.send).toHaveBeenCalledWith(inquiry)
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Tu consulta fue enviada.',
    )
  })

  it('shows a development configuration warning instead of an empty WhatsApp link', () => {
    renderApp(<Contacto api={fakeContactApi()} whatsappNumber="" />)

    expect(
      screen.queryByRole('link', { name: /contactar por whatsapp/i }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('note')).toHaveTextContent(
      /VITE_WHATSAPP_NUMBER/i,
    )
  })
})

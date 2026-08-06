import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/http'
import { AuthProvider } from '../auth/AuthProvider'
import { fakeAuth } from '../test/fakes'
import { renderApp } from '../test/renderApp'
import Header from './Header'

const client = {
  userId: 41,
  clientId: 12,
  email: 'ana@example.cl',
  name: 'Ana Pérez',
  accountType: 'CLIENT',
}

function renderHeader(api = fakeAuth()) {
  return renderApp(
    <AuthProvider api={api}>
      <Header />
    </AuthProvider>,
    { route: '/' },
  )
}

describe('Header authentication control', () => {
  it('exposes an accessible mobile menu that closes after navigation', async () => {
    const { router } = renderHeader()
    const menuButton = await screen.findByRole('button', {
      name: 'Abrir menú',
    })

    expect(menuButton).toHaveAttribute('aria-expanded', 'false')
    expect(menuButton).toHaveAttribute('aria-controls', 'public-navigation')

    await userEvent.click(menuButton)

    expect(screen.getByRole('button', { name: 'Cerrar menú' }))
      .toHaveAttribute('aria-expanded', 'true')

    await userEvent.click(screen.getByRole('link', { name: 'Nosotros' }))

    expect(router.state.location.pathname).toBe('/nosotros')
    expect(screen.getByRole('button', { name: 'Abrir menú' }))
      .toHaveAttribute('aria-expanded', 'false')
  })

  it('shows visitors separate login and booking links', async () => {
    renderHeader()

    expect(await screen.findByRole('link', { name: 'Iniciar sesión' }))
      .toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: 'Pedir hora' }))
      .toHaveAttribute('href', '/reservar')
    expect(screen.queryByRole('link', { name: /panel/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cerrar sesión' }))
      .not.toBeInTheDocument()
  })

  it('returns an authenticated client to the client panel and closes the mobile menu', async () => {
    const { router } = renderHeader(fakeAuth({ me: client }))
    const menuButton = await screen.findByRole('button', { name: 'Abrir menú' })

    await userEvent.click(menuButton)
    const panelLink = screen.getByRole('link', { name: 'Mi panel' })
    expect(panelLink).toHaveAttribute('href', '/panel')

    await userEvent.click(panelLink)

    expect(router.state.location.pathname).toBe('/panel')
    expect(screen.getByRole('button', { name: 'Abrir menú' }))
      .toHaveAttribute('aria-expanded', 'false')
  })

  it('returns an authenticated administrator to the administrator panel', async () => {
    renderHeader(fakeAuth({
      me: {
        ...client,
        clientId: null,
        accountType: 'ADMIN',
      },
    }))

    expect(await screen.findByRole('link', { name: 'Panel admin' }))
      .toHaveAttribute('href', '/admin')
  })

  it('logs an authenticated user out and restores the login link', async () => {
    const api = fakeAuth({ me: client })
    const { router } = renderHeader(api)

    await userEvent.click(await screen.findByRole('button', {
      name: 'Cerrar sesión',
    }))

    expect(api.logout).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole('link', { name: 'Iniciar sesión' }))
      .toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('keeps logout available and reports a safe error when logout fails', async () => {
    const api = fakeAuth({ me: client })
    api.logout.mockRejectedValueOnce(new ApiError(
      'No pudimos comunicarnos con el servidor.',
      { code: 'NETWORK_ERROR' },
    ))
    renderHeader(api)

    await userEvent.click(await screen.findByRole('button', {
      name: 'Cerrar sesión',
    }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos comunicarnos con el servidor.',
    )
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeEnabled()
    expect(screen.queryByRole('link', { name: 'Iniciar sesión' }))
      .not.toBeInTheDocument()
  })
})

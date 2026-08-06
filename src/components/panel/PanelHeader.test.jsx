import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { ApiError } from '../../api/http'
import { AuthProvider } from '../../auth/AuthProvider'
import { fakeAuth } from '../../test/fakes'
import { renderApp } from '../../test/renderApp'
import PanelHeader from './PanelHeader'

const client = {
  userId: 41,
  clientId: 12,
  email: 'ana@example.cl',
  name: 'Ana Pérez',
  accountType: 'CLIENT',
}

function authWrapper(api) {
  return function TestAuthWrapper({ children }) {
    return <AuthProvider api={api}>{children}</AuthProvider>
  }
}

function renderHeader(api) {
  return renderApp(null, {
    route: '/panel',
    routes: [
      {
        path: '/panel',
        element: <PanelHeader onProfileAction={() => {}} />,
      },
      { path: '/login', element: <h1>Iniciar sesión</h1> },
    ],
    Wrapper: authWrapper(api),
  })
}

async function openProfileMenu() {
  await userEvent.click(await screen.findByRole('button', { name: /ana pérez/i }))
}

it('invalidates the server session before leaving the client panel', async () => {
  const api = fakeAuth({ me: client })
  const { router } = renderHeader(api)

  await openProfileMenu()
  await userEvent.click(screen.getByRole('button', { name: /cerrar sesión/i }))

  expect(api.logout).toHaveBeenCalledTimes(1)
  expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
  expect(router.state.location.pathname).toBe('/login')
})

it('keeps the client in place and shows a safe error when logout fails', async () => {
  const api = fakeAuth({ me: client })
  api.logout.mockRejectedValueOnce(new ApiError(
    'No pudimos comunicarnos con el servidor.',
    { code: 'NETWORK_ERROR' },
  ))
  const { router } = renderHeader(api)

  await openProfileMenu()
  await userEvent.click(screen.getByRole('button', { name: /cerrar sesión/i }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'No pudimos comunicarnos con el servidor.',
  )
  expect(router.state.location.pathname).toBe('/panel')
})

it('keeps a direct client-side route back to the client panel', async () => {
  const api = fakeAuth({ me: client })
  const { router } = renderHeader(api)

  const panelLink = await screen.findByRole('link', { name: 'Mi panel' })
  expect(panelLink).toHaveAttribute('href', '/panel')

  await userEvent.click(panelLink)
  expect(router.state.location.pathname).toBe('/panel')
})

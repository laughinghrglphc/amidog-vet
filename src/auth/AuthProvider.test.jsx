import { StrictMode, useState } from 'react'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import {
  BOOKING_DRAFT_KEY,
  LEGACY_BOOKING_DRAFT_KEY,
} from '../hooks/useBookingDraft'
import { fakeAuth } from '../test/fakes'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'

const client = {
  userId: 41,
  clientId: 12,
  email: 'ana@example.cl',
  name: 'Ana Pérez',
  accountType: 'CLIENT',
}

const admin = {
  userId: 1,
  clientId: null,
  email: 'admin@amidog.cl',
  name: 'Administradora AmiDog',
  accountType: 'ADMIN',
}

beforeEach(() => {
  sessionStorage.clear()
})

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

function Probe() {
  const auth = useAuth()
  const [actionError, setActionError] = useState('')

  return (
    <div>
      <output data-testid="loading">{String(auth.loading)}</output>
      <output data-testid="user">{auth.user?.accountType ?? 'anonymous'}</output>
      <output data-testid="error">{auth.error?.message ?? ''}</output>
      <output data-testid="action-error">{actionError}</output>
      <button type="button" onClick={() => auth.refresh()}>Reintentar</button>
      <button
        type="button"
        onClick={() => auth.login('admin@amidog.cl', 'password')
          .catch(() => setActionError('login-failed'))}
      >
        Ingresar
      </button>
      <button
        type="button"
        onClick={() => auth.logout().catch(() => setActionError('logout-failed'))}
      >
        Salir
      </button>
    </div>
  )
}

function renderProvider(api, { strict = false } = {}) {
  const content = (
    <AuthProvider api={api}>
      <Probe />
    </AuthProvider>
  )
  return render(strict ? <StrictMode>{content}</StrictMode> : content)
}

describe('AuthProvider bootstrap and lifecycle', () => {
  it('does not expose a user before the initial session request resolves', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.me.mockReturnValueOnce(pending.promise)

    renderProvider(api)

    expect(screen.getByTestId('loading')).toHaveTextContent('true')
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
    await act(() => pending.resolve(client))
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
    expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
  })

  it('treats only a 401 bootstrap response as an anonymous session', async () => {
    const api = fakeAuth()
    api.me.mockRejectedValueOnce(new ApiError('Autenticación requerida.', {
      code: 'UNAUTHENTICATED',
      status: 401,
    }))

    renderProvider(api)

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('false')
    })
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('error')).toBeEmptyDOMElement()
  })

  it('exposes a safe non-401 bootstrap failure and retries it', async () => {
    const api = fakeAuth()
    api.me
      .mockRejectedValueOnce(new ApiError('No pudimos comunicarnos con el servidor.', {
        code: 'NETWORK_ERROR',
      }))
      .mockResolvedValueOnce(client)

    renderProvider(api)

    await waitFor(() => {
      expect(screen.getByTestId('error')).toHaveTextContent(
        'No pudimos comunicarnos con el servidor.',
      )
    })
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    })
    expect(screen.getByTestId('error')).toBeEmptyDOMElement()
  })

  it.each([
    ['CLIENT', client],
    ['ADMIN', admin],
  ])('loads a verified %s session', async (role, account) => {
    renderProvider(fakeAuth({ me: account }))

    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent(role)
    })
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
  })

  it('loads the current session once under StrictMode', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.me.mockReturnValueOnce(pending.promise)

    renderProvider(api, { strict: true })

    expect(api.me).toHaveBeenCalledTimes(1)
    await act(() => pending.resolve(client))
    expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
  })

  it('prevents a late initial response from overwriting a later login', async () => {
    const pending = deferred()
    const api = fakeAuth({ loginResult: admin })
    api.me.mockReturnValueOnce(pending.promise)

    renderProvider(api)
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('ADMIN')
      expect(screen.getByTestId('loading')).toHaveTextContent('false')
    })

    await act(() => pending.resolve(client))
    expect(screen.getByTestId('user')).toHaveTextContent('ADMIN')
  })

  it('prevents a late initial response from restoring a logged-out session', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.me.mockReturnValueOnce(pending.promise)

    renderProvider(api)
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')

    await act(() => pending.resolve(client))
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
  })

  it('prevents a late initial response from overwriting a manual refresh', async () => {
    const initial = deferred()
    const api = fakeAuth()
    api.me
      .mockReturnValueOnce(initial.promise)
      .mockResolvedValueOnce(admin)

    renderProvider(api)
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('ADMIN')
      expect(screen.getByTestId('loading')).toHaveTextContent('false')
    })

    await act(() => initial.resolve(client))
    expect(screen.getByTestId('user')).toHaveTextContent('ADMIN')
  })

  it('prevents a late initial response from undoing an unauthorized event', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.me.mockReturnValueOnce(pending.promise)

    renderProvider(api)
    act(() => window.dispatchEvent(new CustomEvent('amidog:unauthorized')))
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')

    await act(() => pending.resolve(client))
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
  })

  it('prevents a late refresh response from restoring a logged-out user', async () => {
    const refresh = deferred()
    const api = fakeAuth({ me: client })

    renderProvider(api)
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    })
    api.me.mockReturnValueOnce(refresh.promise)
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')

    await act(() => refresh.resolve(client))
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
  })

  it('clears stale state on unauthorized and ignores an older login response', async () => {
    const login = deferred()
    const api = fakeAuth({ me: client })
    api.login.mockReturnValueOnce(login.promise)

    renderProvider(api)
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    })
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
    act(() => window.dispatchEvent(new CustomEvent('amidog:unauthorized')))
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')

    await act(() => login.resolve(admin))
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
  })

  it('settles loading when a failed login supersedes a pending bootstrap', async () => {
    const bootstrap = deferred()
    const api = fakeAuth()
    api.me.mockReturnValueOnce(bootstrap.promise)
    api.login.mockRejectedValueOnce(new ApiError(
      'Correo o contraseña incorrectos, o cuenta aún no verificada.',
      { code: 'INVALID_CREDENTIALS', status: 401 },
    ))

    renderProvider(api)
    expect(screen.getByTestId('loading')).toHaveTextContent('true')
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    await waitFor(() => {
      expect(screen.getByTestId('action-error')).toHaveTextContent('login-failed')
      expect(screen.getByTestId('loading')).toHaveTextContent('false')
    })
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('error')).toBeEmptyDOMElement()

    await act(() => bootstrap.resolve(client))
    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
  })

  it('settles loading and preserves the user when failed logout supersedes refresh', async () => {
    const refresh = deferred()
    const api = fakeAuth({ me: client })
    api.logout.mockRejectedValueOnce(new ApiError(
      'No pudimos comunicarnos con el servidor.',
      { code: 'NETWORK_ERROR' },
    ))

    renderProvider(api)
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
      expect(screen.getByTestId('loading')).toHaveTextContent('false')
    })
    api.me.mockReturnValueOnce(refresh.promise)
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(screen.getByTestId('loading')).toHaveTextContent('true')
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))

    await waitFor(() => {
      expect(screen.getByTestId('action-error')).toHaveTextContent('logout-failed')
      expect(screen.getByTestId('loading')).toHaveTextContent('false')
    })
    expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    expect(screen.getByTestId('error')).toBeEmptyDOMElement()

    await act(() => refresh.resolve(admin))
    expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
  })

  it('preserves the current user when logout fails', async () => {
    const api = fakeAuth({ me: client })
    api.logout.mockRejectedValueOnce(new ApiError('No se pudo cerrar la sesión.', {
      code: 'NETWORK_ERROR',
    }))

    renderProvider(api)
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    })
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))

    await waitFor(() => {
      expect(screen.getByTestId('action-error')).toHaveTextContent('logout-failed')
    })
    expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
  })

  it('clears current and legacy booking drafts after successful logout', async () => {
    sessionStorage.setItem(BOOKING_DRAFT_KEY, '{"private":"note"}')
    sessionStorage.setItem(LEGACY_BOOKING_DRAFT_KEY, '{"private":"legacy"}')
    renderProvider(fakeAuth({ me: client }))
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    })

    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))

    expect(screen.getByTestId('user')).toHaveTextContent('anonymous')
    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).toBeNull()
    expect(sessionStorage.getItem(LEGACY_BOOKING_DRAFT_KEY)).toBeNull()
  })

  it('keeps both booking drafts when logout fails', async () => {
    const api = fakeAuth({ me: client })
    api.logout.mockRejectedValueOnce(new ApiError('Sin conexión.', {
      code: 'NETWORK_ERROR',
    }))
    sessionStorage.setItem(BOOKING_DRAFT_KEY, '{"private":"note"}')
    sessionStorage.setItem(LEGACY_BOOKING_DRAFT_KEY, '{"private":"legacy"}')
    renderProvider(api)
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    })

    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))
    await screen.findByText('logout-failed')

    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).not.toBeNull()
    expect(sessionStorage.getItem(LEGACY_BOOKING_DRAFT_KEY)).not.toBeNull()
  })

  it('clears a prior account draft before exposing a successful new login', async () => {
    sessionStorage.setItem(BOOKING_DRAFT_KEY, '{"private":"account-one"}')
    sessionStorage.setItem(LEGACY_BOOKING_DRAFT_KEY, '{"private":"legacy"}')
    renderProvider(fakeAuth({ me: client, loginResult: admin }))
    await waitFor(() => {
      expect(screen.getByTestId('user')).toHaveTextContent('CLIENT')
    })

    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByTestId('user')).toHaveTextContent('ADMIN')
    expect(sessionStorage.getItem(BOOKING_DRAFT_KEY)).toBeNull()
    expect(sessionStorage.getItem(LEGACY_BOOKING_DRAFT_KEY)).toBeNull()
  })

  it('removes the global unauthorized listener on unmount', () => {
    const add = vi.spyOn(window, 'addEventListener')
    const remove = vi.spyOn(window, 'removeEventListener')

    const view = renderProvider(fakeAuth())
    const added = add.mock.calls.find(([name]) => name === 'amidog:unauthorized')
    expect(added).toBeDefined()

    view.unmount()
    expect(remove).toHaveBeenCalledWith('amidog:unauthorized', added[1])
  })

  it('ignores an initial response after the provider unmounts', async () => {
    const pending = deferred()
    const api = fakeAuth()
    api.me.mockReturnValueOnce(pending.promise)

    const view = renderProvider(api)
    view.unmount()
    await act(() => pending.resolve(client))

    expect(screen.queryByTestId('user')).not.toBeInTheDocument()
  })

  it('never persists a session or credentials in browser storage', async () => {
    const localWrite = vi.spyOn(Storage.prototype, 'setItem')
    const api = fakeAuth({ loginResult: admin })
    renderProvider(api)

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('false')
    })
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salir' }))

    expect(localWrite).not.toHaveBeenCalled()
  })
})

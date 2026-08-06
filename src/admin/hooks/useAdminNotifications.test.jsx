import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import useAdminNotifications from './useAdminNotifications'

function NotificationHarness({ api, interval = 30_000 }) {
  const notifications = useAdminNotifications(api, interval)

  return (
    <div>
      <output aria-label="Notificaciones sin leer">
        {notifications.unreadCount}
      </output>
      {notifications.loading && <span>Cargando</span>}
      {notifications.error && (
        <span role="alert">{notifications.error.message}</span>
      )}
    </div>
  )
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

describe('useAdminNotifications', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads notifications and derives the unread count from server state', async () => {
    const api = {
      notifications: vi.fn().mockResolvedValue([
        {
          id: 1,
          type: 'NEW_RESERVATION',
          title: 'Nueva reserva',
          body: 'Uno',
          reservationId: 10,
          createdAt: '2026-08-05T12:00:00Z',
          unread: true,
        },
        {
          id: 2,
          type: 'NEW_RESERVATION',
          title: 'Reserva revisada',
          body: 'Dos',
          reservationId: 11,
          createdAt: '2026-08-05T12:01:00Z',
          unread: false,
        },
      ]),
    }

    render(<NotificationHarness api={api} />)

    expect(await screen.findByLabelText('Notificaciones sin leer'))
      .toHaveTextContent('1')
    expect(api.notifications).toHaveBeenCalledTimes(1)
    expect(screen.queryByText('Cargando')).not.toBeInTheDocument()
  })

  it('polls once per interval while visible and refreshes when the window regains focus', async () => {
    vi.useFakeTimers()
    const api = { notifications: vi.fn().mockResolvedValue([]) }

    render(<NotificationHarness api={api} interval={30_000} />)

    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(api.notifications).toHaveBeenCalledTimes(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })
    expect(api.notifications).toHaveBeenCalledTimes(2)

    await act(async () => {
      window.dispatchEvent(new Event('focus'))
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(api.notifications).toHaveBeenCalledTimes(3)
  })

  it('skips hidden polling and refreshes as soon as the document becomes visible', async () => {
    vi.useFakeTimers()
    let visibility = 'hidden'
    vi.spyOn(document, 'visibilityState', 'get')
      .mockImplementation(() => visibility)
    const api = { notifications: vi.fn().mockResolvedValue([]) }

    render(<NotificationHarness api={api} interval={30_000} />)
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })
    expect(api.notifications).toHaveBeenCalledTimes(1)

    visibility = 'visible'
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'))
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(api.notifications).toHaveBeenCalledTimes(2)
  })

  it('retains the last unread count when a background refresh fails', async () => {
    vi.useFakeTimers()
    const api = {
      notifications: vi.fn()
        .mockResolvedValueOnce([{
          id: 1,
          type: 'NEW_RESERVATION',
          title: 'Nueva reserva',
          body: 'Uno',
          reservationId: 10,
          createdAt: '2026-08-05T12:00:00Z',
          unread: true,
        }])
        .mockRejectedValueOnce(new Error('Sin conexión')),
    }

    render(<NotificationHarness api={api} interval={30_000} />)
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(screen.getByLabelText('Notificaciones sin leer')).toHaveTextContent('1')

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })

    expect(screen.getByLabelText('Notificaciones sin leer')).toHaveTextContent('1')
    expect(screen.getByRole('alert')).toHaveTextContent('Sin conexión')
  })

  it('reuses an in-flight refresh when polling and focus happen together', async () => {
    vi.useFakeTimers()
    const pendingRefresh = deferred()
    const api = {
      notifications: vi.fn()
        .mockResolvedValueOnce([])
        .mockReturnValueOnce(pendingRefresh.promise),
    }

    render(<NotificationHarness api={api} interval={30_000} />)
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
      window.dispatchEvent(new Event('focus'))
      await Promise.resolve()
    })
    expect(api.notifications).toHaveBeenCalledTimes(2)

    await act(async () => {
      pendingRefresh.resolve([])
      await pendingRefresh.promise
    })
  })
})

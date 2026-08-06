import { useState } from 'react'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import useAdminDashboard from './useAdminDashboard'

function deferred() {
  let reject
  let resolve
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

function dashboard(name) {
  return {
    profile: {
      name,
      email: `${name.toLocaleLowerCase('es-CL')}@amidog.cl`,
      role: 'ADMIN',
    },
    stats: { todayAppointments: 0, clients: 0, pets: 0 },
    appointments: [],
    services: [],
    chart: {
      currentWeek: { label: 'Esta semana', labels: [], values: [] },
      previousWeek: { label: 'Semana pasada', labels: [], values: [] },
      currentMonth: { label: 'Este mes', labels: [], values: [] },
    },
    schedule: [],
  }
}

function DashboardHarness({ api }) {
  const state = useAdminDashboard(api)
  const [reloadStatus, setReloadStatus] = useState('')
  return (
    <section>
      <p>Perfil: {state.data?.profile.name ?? 'sin datos'}</p>
      <p>Cargando: {state.loading ? 'sí' : 'no'}</p>
      <p>Error: {state.error?.message ?? 'ninguno'}</p>
      <output data-testid="reload-status">{reloadStatus}</output>
      <button
        type="button"
        onClick={() => state.reload().then((result) =>
          setReloadStatus(result?.status ?? ''))}
      >
        Recargar
      </button>
    </section>
  )
}

describe('useAdminDashboard request generations', () => {
  it('returns an explicit failed reload result for mutation composition', async () => {
    const failure = new Error('Sin conexión')
    const api = {
      dashboard: vi.fn()
        .mockResolvedValueOnce(dashboard('Inicial'))
        .mockRejectedValueOnce(failure),
    }
    const user = userEvent.setup()
    render(<DashboardHarness api={api} />)
    expect(await screen.findByText('Perfil: Inicial')).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Recargar' }))

    expect(await screen.findByTestId('reload-status')).toHaveTextContent('failure')
    expect(screen.getByText('Perfil: Inicial')).toBeVisible()
  })

  it('ignores an older reload completion while the current reload remains pending', async () => {
    const older = deferred()
    const current = deferred()
    const api = {
      dashboard: vi.fn()
        .mockResolvedValueOnce(dashboard('Inicial'))
        .mockReturnValueOnce(older.promise)
        .mockReturnValueOnce(current.promise),
    }
    const user = userEvent.setup()
    render(<DashboardHarness api={api} />)

    expect(await screen.findByText('Perfil: Inicial')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Recargar' }))
    await user.click(screen.getByRole('button', { name: 'Recargar' }))

    await act(async () => {
      older.resolve(dashboard('Antiguo'))
      await older.promise
    })

    expect(screen.getByText('Perfil: Inicial')).toBeVisible()
    expect(screen.getByText('Cargando: sí')).toBeVisible()

    await act(async () => {
      current.resolve(dashboard('Actual'))
      await current.promise
    })

    expect(screen.getByText('Perfil: Actual')).toBeVisible()
    expect(screen.getByText('Cargando: no')).toBeVisible()
  })

  it('keeps a newer API-prop result when the initial request settles out of order', async () => {
    const oldRequest = deferred()
    const newRequest = deferred()
    const oldApi = { dashboard: vi.fn().mockReturnValue(oldRequest.promise) }
    const newApi = { dashboard: vi.fn().mockReturnValue(newRequest.promise) }
    const { rerender } = render(<DashboardHarness api={oldApi} />)

    rerender(<DashboardHarness api={newApi} />)
    await act(async () => {
      newRequest.resolve(dashboard('Nueva'))
      await newRequest.promise
    })
    expect(screen.getByText('Perfil: Nueva')).toBeVisible()

    await act(async () => {
      oldRequest.resolve(dashboard('Antigua'))
      await oldRequest.promise
    })
    expect(screen.getByText('Perfil: Nueva')).toBeVisible()
  })

  it('settles initial and retry promises safely after unmount', async () => {
    const initial = deferred()
    const retry = deferred()
    const api = {
      dashboard: vi.fn()
        .mockReturnValueOnce(initial.promise)
        .mockReturnValueOnce(retry.promise),
    }
    const { unmount } = render(<DashboardHarness api={api} />)

    await userEvent.click(screen.getByRole('button', { name: 'Recargar' }))
    unmount()

    await act(async () => {
      initial.resolve(dashboard('Inicial tardía'))
      retry.resolve(dashboard('Recarga tardía'))
      await Promise.all([initial.promise, retry.promise])
    })

    expect(screen.queryByText(/Perfil:/)).not.toBeInTheDocument()
  })
})

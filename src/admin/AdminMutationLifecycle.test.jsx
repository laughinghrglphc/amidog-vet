import { useEffect } from 'react'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import AvailabilityManager from './components/availability/AvailabilityManager'
import ClientManager from './components/clients/ClientManager'
import NotificationManager from './components/notifications/NotificationManager'
import PetManager from './components/pets/PetManager'
import ServiceManager from './components/services/ServiceManager'
import useAdminNotifications from './hooks/useAdminNotifications'
import useManagementResource from './hooks/useManagementResource'

function deferred() {
  let reject
  let resolve
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

function NotificationManagerHarness({ api }) {
  const notifications = useAdminNotifications(api, 60_000)
  return <NotificationManager api={api} notifications={notifications} />
}

const page = (content) => ({
  content,
  page: 0,
  size: 25,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
})

const service = {
  id: 7,
  code: 'consulta-general',
  name: 'Consulta general',
  description: 'Evaluación general',
  active: true,
  displayOrder: 10,
}

const secondService = {
  id: 8,
  code: 'vacunacion',
  name: 'Vacunación',
  description: 'Vacuna anual',
  active: true,
  displayOrder: 20,
}

const block = {
  id: 90,
  startsAt: '2026-08-15T00:00:00-04:00',
  endsAt: '2026-08-16T00:00:00-04:00',
  reason: 'Cirugía externa',
  createdAt: '2026-08-01T09:00:00-04:00',
}

const clientSummary = {
  id: 12,
  name: 'Ana Pérez',
  email: 'ana@example.cl',
  phone: '+56912345678',
  active: true,
  createdAt: '2025-01-02T10:00:00-03:00',
  updatedAt: '2026-07-01T10:00:00-04:00',
}

const clientDetail = {
  ...clientSummary,
  pets: [],
  reservationCounts: {
    total: 0,
    upcoming: 0,
    pending: 0,
    confirmed: 0,
    cancelled: 0,
    completed: 0,
    noShow: 0,
  },
  upcomingReservations: [],
}

const petSummary = {
  id: 84,
  clientId: 12,
  ownerName: 'Ana Pérez',
  name: 'Milo',
  species: 'Gato',
  breed: 'Mestizo',
  birthdate: '2022-05-10',
  active: true,
  createdAt: '2022-05-10T10:00:00-04:00',
  updatedAt: '2026-07-01T10:00:00-04:00',
}

const petDetail = {
  ...petSummary,
  ownerEmail: 'ana@example.cl',
  recentReservations: [],
}

function ResourceHarness({ api, onReady }) {
  const resource = useManagementResource(() => api.load(), [api])
  useEffect(() => {
    onReady(resource.reload)
  }, [onReady, resource.reload])
  return <p>{resource.data ?? 'cargando'}</p>
}

describe('administrator mutation lifetimes', () => {
  it('makes a resource reload a no-op after its owner is disposed', async () => {
    const api = { load: vi.fn().mockResolvedValue('listo') }
    let reload
    const { unmount } = render(
      <ResourceHarness api={api} onReady={(value) => { reload = value }} />,
    )
    expect(await screen.findByText('listo')).toBeVisible()
    unmount()

    let result
    await act(async () => {
      result = await reload()
    })

    expect(api.load).toHaveBeenCalledTimes(1)
    expect(result).toEqual({ status: 'disposed' })
  })

  it('returns an explicit failure result from a rejected resource reload', async () => {
    const failure = new Error('Sin conexiÃ³n')
    const api = {
      load: vi.fn()
        .mockResolvedValueOnce('listo')
        .mockRejectedValueOnce(failure),
    }
    let reload
    render(<ResourceHarness api={api} onReady={(value) => { reload = value }} />)
    expect(await screen.findByText('listo')).toBeVisible()

    let result
    await act(async () => {
      result = await reload()
    })

    expect(result).toEqual({ error: failure, status: 'failure' })
    expect(screen.getByText('listo')).toBeVisible()
  })

  it('marks an older resource request stale instead of reporting it as success', async () => {
    const older = deferred()
    const current = deferred()
    const api = {
      load: vi.fn()
        .mockResolvedValueOnce('inicial')
        .mockReturnValueOnce(older.promise)
        .mockReturnValueOnce(current.promise),
    }
    let reload
    render(<ResourceHarness api={api} onReady={(value) => { reload = value }} />)
    expect(await screen.findByText('inicial')).toBeVisible()

    let olderResult
    let currentResult
    act(() => {
      olderResult = reload()
      currentResult = reload()
    })
    await act(async () => {
      older.resolve('antiguo')
      current.resolve('actual')
      olderResult = await olderResult
      currentResult = await currentResult
    })

    expect(olderResult).toEqual({ status: 'stale' })
    expect(currentResult).toEqual({ status: 'success', value: 'actual' })
    expect(screen.getByText('actual')).toBeVisible()
  })

  it('keeps an authoritative created service and offers reload-only retry after refresh failure', async () => {
    const created = {
      id: 9,
      code: 'peluqueria',
      name: 'PeluquerÃ­a',
      description: null,
      active: true,
      displayOrder: 30,
    }
    const api = {
      services: vi.fn()
        .mockResolvedValueOnce([service])
        .mockRejectedValueOnce(new Error('Sin conexiÃ³n'))
        .mockResolvedValueOnce([service, created]),
      archiveService: vi.fn(),
      updateService: vi.fn(),
      createService: vi.fn().mockResolvedValue(created),
    }
    const user = userEvent.setup()
    render(<ServiceManager api={api} />)
    await screen.findByText('Consulta general')
    await user.click(screen.getByRole('button', { name: 'Crear servicio' }))
    await user.type(screen.getByLabelText(/digo$/i), 'peluqueria')
    await user.type(screen.getByLabelText('Nombre'), 'PeluquerÃ­a')
    await user.clear(screen.getByLabelText('Orden'))
    await user.type(screen.getByLabelText('Orden'), '30')
    await user.click(screen.getByRole('button', { name: 'Guardar servicio' }))

    expect(await screen.findByText('PeluquerÃ­a')).toBeVisible()
    expect(screen.getByRole('alert')).toHaveTextContent(
      /servicio se guard.*no pudimos actualizar/i,
    )
    expect(screen.queryByText('Servicio creado.')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Reintentar actualiz/i }))

    expect(api.createService).toHaveBeenCalledTimes(1)
    expect(api.services).toHaveBeenCalledTimes(3)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('marks a 204 service archive locally and never repeats DELETE on refresh retry', async () => {
    const archived = { ...service, active: false }
    const api = {
      services: vi.fn()
        .mockResolvedValueOnce([service])
        .mockRejectedValueOnce(new Error('Sin conexiÃ³n'))
        .mockResolvedValueOnce([archived]),
      archiveService: vi.fn().mockResolvedValue(undefined),
      updateService: vi.fn(),
      createService: vi.fn(),
    }
    const user = userEvent.setup()
    render(<ServiceManager api={api} />)
    await user.click(await screen.findByRole('button', {
      name: 'Archivar Consulta general',
    }))

    expect(await screen.findByRole('button', {
      name: 'Reactivar Consulta general',
    })).toBeVisible()
    expect(screen.getByRole('alert')).toHaveTextContent(
      /servicio se archiv.*no pudimos actualizar/i,
    )
    await user.click(screen.getByRole('button', { name: /Reintentar actualiz/i }))

    expect(api.archiveService).toHaveBeenCalledTimes(1)
    expect(api.services).toHaveBeenCalledTimes(3)
  })

  it('deduplicates service archive and performs no reload after unmount', async () => {
    const completion = deferred()
    const api = {
      services: vi.fn().mockResolvedValue([service]),
      archiveService: vi.fn().mockReturnValue(completion.promise),
      updateService: vi.fn(),
      createService: vi.fn(),
    }
    const { unmount } = render(<ServiceManager api={api} />)
    const button = await screen.findByRole('button', { name: 'Archivar Consulta general' })

    act(() => {
      button.click()
      button.click()
    })
    expect(api.archiveService).toHaveBeenCalledTimes(1)
    unmount()
    await act(async () => {
      completion.resolve(undefined)
      await completion.promise
    })
    expect(api.services).toHaveBeenCalledTimes(1)
  })

  it('serializes different service operations through each completed truth refresh', async () => {
    const first = deferred()
    const second = deferred()
    const api = {
      services: vi.fn()
        .mockResolvedValueOnce([service, secondService])
        .mockResolvedValueOnce([
          { ...service, active: false },
          secondService,
        ])
        .mockResolvedValueOnce([
          { ...service, active: false },
          { ...secondService, active: false },
        ]),
      archiveService: vi.fn((id) => id === service.id
        ? first.promise
        : second.promise),
      updateService: vi.fn(),
      createService: vi.fn(),
    }
    render(<ServiceManager api={api} />)
    const firstArchive = await screen.findByRole('button', {
      name: 'Archivar Consulta general',
    })
    const secondArchive = screen.getByRole('button', {
      name: 'Archivar Vacunación',
    })

    act(() => {
      firstArchive.click()
      secondArchive.click()
      firstArchive.click()
    })

    expect(api.archiveService.mock.calls.map(([id]) => id)).toEqual([7])
    expect(screen.getByRole('button', { name: 'Crear servicio' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Editar Vacunación' })).toBeDisabled()
    expect(secondArchive).toBeDisabled()

    await act(async () => {
      first.resolve(undefined)
      await first.promise
    })
    await waitFor(() => {
      expect(screen.getByRole('button', {
        name: 'Reactivar Consulta general',
      })).toBeEnabled()
    })

    const permittedSecond = screen.getByRole('button', {
      name: 'Archivar Vacunación',
    })
    act(() => {
      permittedSecond.click()
      permittedSecond.click()
    })
    expect(api.archiveService.mock.calls.map(([id]) => id)).toEqual([7, 8])

    await act(async () => {
      second.resolve(undefined)
      await second.promise
    })
    expect(await screen.findByRole('button', {
      name: 'Reactivar Vacunación',
    })).toBeEnabled()
    expect(screen.getByRole('button', {
      name: 'Reactivar Consulta general',
    })).toBeEnabled()
    expect(api.services).toHaveBeenCalledTimes(3)
  })

  it('deduplicates weekly replacement and performs no reload after unmount', async () => {
    const completion = deferred()
    const api = {
      weeklyAvailability: vi.fn().mockResolvedValue([{
        id: 1,
        dayOfWeek: 1,
        start: '09:00:00',
        end: '13:00:00',
        active: true,
      }]),
      blocks: vi.fn().mockResolvedValue([]),
      replaceWeeklyAvailability: vi.fn().mockReturnValue(completion.promise),
      createBlock: vi.fn(),
      deleteBlock: vi.fn(),
    }
    const { unmount } = render(<AvailabilityManager api={api} />)
    const button = await screen.findByRole('button', { name: 'Guardar horario semanal' })
    act(() => {
      button.click()
      button.click()
    })
    expect(api.replaceWeeklyAvailability).toHaveBeenCalledTimes(1)
    unmount()
    await act(async () => {
      completion.resolve([])
      await completion.promise
    })
    expect(api.weeklyAvailability).toHaveBeenCalledTimes(1)
  })

  it('deduplicates block deletion and performs no reload after unmount', async () => {
    const completion = deferred()
    const api = {
      weeklyAvailability: vi.fn().mockResolvedValue([]),
      blocks: vi.fn().mockResolvedValue([block]),
      replaceWeeklyAvailability: vi.fn(),
      createBlock: vi.fn(),
      deleteBlock: vi.fn().mockReturnValue(completion.promise),
    }
    const { unmount } = render(<AvailabilityManager api={api} />)
    const button = await screen.findByRole('button', { name: 'Eliminar bloqueo 90' })
    act(() => {
      button.click()
      button.click()
    })
    expect(api.deleteBlock).toHaveBeenCalledTimes(1)
    unmount()
    await act(async () => {
      completion.resolve(undefined)
      await completion.promise
    })
    expect(api.blocks).toHaveBeenCalledTimes(1)
  })

  it('keeps block success status mounted while the affected resource reloads', async () => {
    const reload = deferred()
    const api = {
      weeklyAvailability: vi.fn().mockResolvedValue([]),
      blocks: vi.fn()
        .mockResolvedValueOnce([])
        .mockReturnValueOnce(reload.promise),
      replaceWeeklyAvailability: vi.fn(),
      createBlock: vi.fn().mockResolvedValue(block),
      deleteBlock: vi.fn(),
    }
    const user = userEvent.setup()
    render(<AvailabilityManager api={api} />)
    await screen.findByRole('heading', { name: 'Bloqueos excepcionales' })
    await user.type(screen.getByLabelText('Inicio del bloqueo'), '2026-08-10T10:00')
    await user.type(screen.getByLabelText('Fin del bloqueo'), '2026-08-10T12:00')
    await user.click(screen.getByRole('button', { name: 'Crear bloqueo' }))
    await waitFor(() => expect(api.blocks).toHaveBeenCalledTimes(2))

    expect(screen.getByRole('heading', { name: 'Bloqueos excepcionales' })).toBeVisible()
    await act(async () => {
      reload.resolve([block])
      await reload.promise
    })
    expect(await screen.findByRole('status')).toHaveTextContent('Bloqueo creado')
  })

  it('keeps an authoritative block and retries only its failed refresh', async () => {
    const api = {
      weeklyAvailability: vi.fn().mockResolvedValue([]),
      blocks: vi.fn()
        .mockResolvedValueOnce([])
        .mockRejectedValueOnce(new Error('Sin conexión'))
        .mockResolvedValueOnce([block]),
      replaceWeeklyAvailability: vi.fn(),
      createBlock: vi.fn().mockResolvedValue(block),
      deleteBlock: vi.fn(),
    }
    const user = userEvent.setup()
    render(<AvailabilityManager api={api} />)
    await screen.findByRole('heading', { name: 'Bloqueos excepcionales' })
    await user.type(screen.getByLabelText('Inicio del bloqueo'), '2026-08-10T10:00')
    await user.type(screen.getByLabelText('Fin del bloqueo'), '2026-08-10T12:00')
    await user.click(screen.getByRole('button', { name: 'Crear bloqueo' }))

    expect(await screen.findByText(/Cirug.*externa/i)).toBeVisible()
    expect(screen.getByRole('alert')).toHaveTextContent(
      /bloqueo se guard.*no pudimos actualizar/i,
    )
    await user.click(screen.getByRole('button', { name: /Reintentar actualiz/i }))

    expect(api.createBlock).toHaveBeenCalledTimes(1)
    expect(api.blocks).toHaveBeenCalledTimes(3)
  })

  it.each([
    {
      label: 'client',
      Manager: ClientManager,
      api: {
        clients: vi.fn().mockResolvedValue(page([clientSummary])),
        client: vi.fn().mockResolvedValue(clientDetail),
        updateClient: vi.fn(),
      },
      row: /Ana Pérez/i,
      save: 'Guardar cliente',
      mutation: 'updateClient',
      list: 'clients',
      detail: 'client',
    },
    {
      label: 'pet',
      Manager: PetManager,
      api: {
        pets: vi.fn().mockResolvedValue(page([petSummary])),
        pet: vi.fn().mockResolvedValue(petDetail),
        updatePet: vi.fn(),
      },
      row: /Milo/i,
      save: 'Guardar mascota',
      mutation: 'updatePet',
      list: 'pets',
      detail: 'pet',
    },
  ])('does not reload $label resources after an update settles post-unmount', async ({
    Manager,
    api,
    row,
    save,
    mutation,
    list,
    detail,
  }) => {
    const completion = deferred()
    api[mutation].mockReturnValue(completion.promise)
    const dashboardReload = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    const { unmount } = render(
      <Manager api={api} onDashboardReload={dashboardReload} />,
    )
    await user.click(await screen.findByRole('button', { name: row }))
    await user.click(await screen.findByRole('button', { name: save }))
    unmount()
    await act(async () => {
      completion.resolve(undefined)
      await completion.promise
    })

    expect(api[list]).toHaveBeenCalledTimes(1)
    expect(api[detail]).toHaveBeenCalledTimes(1)
    expect(dashboardReload).not.toHaveBeenCalled()
  })

  it.each([
    {
      label: 'client',
      Manager: ClientManager,
      summary: clientSummary,
      detailValue: clientDetail,
      listMethod: 'clients',
      detailMethod: 'client',
      updateMethod: 'updateClient',
      row: /Ana P.rez/i,
      save: 'Guardar cliente',
    },
    {
      label: 'pet',
      Manager: PetManager,
      summary: petSummary,
      detailValue: petDetail,
      listMethod: 'pets',
      detailMethod: 'pet',
      updateMethod: 'updatePet',
      row: /Milo/i,
      save: 'Guardar mascota',
    },
  ])('keeps an authoritative $label update and retries refresh without a second mutation', async ({
    Manager,
    summary,
    detailValue,
    listMethod,
    detailMethod,
    updateMethod,
    row,
    save,
  }) => {
    const updated = { ...summary, updatedAt: '2026-08-02T11:00:00-04:00' }
    const api = {
      [listMethod]: vi.fn()
        .mockResolvedValueOnce(page([summary]))
        .mockRejectedValueOnce(new Error('Sin conexión'))
        .mockResolvedValueOnce(page([updated])),
      [detailMethod]: vi.fn()
        .mockResolvedValueOnce(detailValue)
        .mockResolvedValue({ ...detailValue, ...updated }),
      [updateMethod]: vi.fn().mockResolvedValue(updated),
    }
    const user = userEvent.setup()
    render(
      <Manager
        api={api}
        onDashboardReload={vi.fn().mockResolvedValue(undefined)}
      />,
    )
    await user.click(await screen.findByRole('button', { name: row }))
    await user.click(await screen.findByRole('button', { name: save }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /cambio se guard.*no pudimos actualizar/i,
    )
    await user.click(screen.getByRole('button', { name: /Reintentar actualiz/i }))

    expect(api[updateMethod]).toHaveBeenCalledTimes(1)
    expect(api[listMethod]).toHaveBeenCalledTimes(3)
  })

  it('marks a notification read locally and retries only its failed refresh', async () => {
    const unread = {
      id: 901,
      type: 'NEW_RESERVATION',
      title: 'Nueva reserva',
      body: 'Se registró una reserva.',
      reservationId: 301,
      createdAt: '2026-08-02T15:10:00Z',
      unread: true,
    }
    const read = { ...unread, unread: false }
    const api = {
      notifications: vi.fn()
        .mockResolvedValueOnce([unread])
        .mockRejectedValueOnce(new Error('Sin conexión'))
        .mockResolvedValueOnce([read]),
      readNotification: vi.fn().mockResolvedValue(read),
      readAllNotifications: vi.fn(),
    }
    const user = userEvent.setup()
    render(<NotificationManagerHarness api={api} />)
    await user.click(await screen.findByRole('button', {
      name: /Marcar Nueva reserva como le.da/i,
    }))

    expect(screen.queryByRole('button', {
      name: /Marcar Nueva reserva como le.da/i,
    })).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(
      /notificaci.*guard.*no pudimos actualizar/i,
    )
    await user.click(screen.getByRole('button', { name: /Reintentar actualiz/i }))

    expect(api.readNotification).toHaveBeenCalledTimes(1)
    expect(api.notifications).toHaveBeenCalledTimes(3)
  })

  it('serializes different notification reads through each completed truth refresh', async () => {
    const firstCompletion = deferred()
    const secondCompletion = deferred()
    const notification = (id, title) => ({
      id,
      type: 'NEW_RESERVATION',
      title,
      body: `${title}.`,
      reservationId: id,
      createdAt: '2026-08-02T15:10:00Z',
      unread: true,
    })
    const api = {
      notifications: vi.fn()
        .mockResolvedValueOnce([
          notification(901, 'Primera reserva'),
          notification(902, 'Segunda reserva'),
        ])
        .mockResolvedValueOnce([
          { ...notification(901, 'Primera reserva'), unread: false },
          notification(902, 'Segunda reserva'),
        ])
        .mockResolvedValueOnce([
          { ...notification(901, 'Primera reserva'), unread: false },
          { ...notification(902, 'Segunda reserva'), unread: false },
        ]),
      readNotification: vi.fn((id) => id === 901
        ? firstCompletion.promise
        : secondCompletion.promise),
      readAllNotifications: vi.fn(),
    }
    render(<NotificationManagerHarness api={api} />)
    const first = await screen.findByRole('button', {
      name: 'Marcar Primera reserva como leída',
    })
    const second = screen.getByRole('button', {
      name: 'Marcar Segunda reserva como leída',
    })
    act(() => {
      first.click()
      second.click()
      first.click()
    })

    expect(api.readNotification.mock.calls.map(([id]) => id)).toEqual([901])
    expect(screen.getByRole('button', {
      name: 'Marcar todas como leídas',
    })).toBeDisabled()
    expect(second).toBeDisabled()

    await act(async () => {
      firstCompletion.resolve(notification(901, 'Primera reserva'))
      await firstCompletion.promise
    })
    await waitFor(() => {
      expect(screen.queryByRole('button', {
        name: 'Marcar Primera reserva como leída',
      })).not.toBeInTheDocument()
    })

    const permittedSecond = screen.getByRole('button', {
      name: 'Marcar Segunda reserva como leída',
    })
    act(() => {
      permittedSecond.click()
      permittedSecond.click()
    })
    expect(api.readNotification.mock.calls.map(([id]) => id))
      .toEqual([901, 902])
    await act(async () => {
      secondCompletion.resolve(notification(902, 'Segunda reserva'))
      await secondCompletion.promise
    })
    await waitFor(() => {
      expect(screen.queryAllByRole('button', {
        name: /^Marcar (Primera|Segunda) reserva como leída$/,
      })).toHaveLength(0)
    })
    expect(screen.getByText('Primera reserva').closest('article'))
      .not.toHaveClass('is-unread')
    expect(screen.getByText('Segunda reserva').closest('article'))
      .not.toHaveClass('is-unread')
    expect(api.notifications).toHaveBeenCalledTimes(3)
  })
})

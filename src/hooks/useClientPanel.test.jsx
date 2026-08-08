import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/http'
import { fakeClientApi } from '../test/fakes'
import { useClientPanel } from './useClientPanel'

const profile = {
  id: 12,
  name: 'Ana Pérez',
  phone: '+56912345678',
  email: 'ana@example.cl',
}

const milo = {
  id: 84,
  name: 'Milo',
  species: 'Gato',
  breed: 'Mestizo',
  birthdate: '2022-05-10',
  active: true,
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

async function waitUntilLoaded(result) {
  await waitFor(() => expect(result.current.loading).toBe(false))
}

describe('useClientPanel', () => {
  it('loads all four resources and exposes their empty collections', async () => {
    const api = fakeClientApi({ profile })
    const { result } = renderHook(() => useClientPanel(api))

    await waitUntilLoaded(result)

    expect(result.current.profile).toEqual(profile)
    expect(result.current.pets).toEqual([])
    expect(result.current.reservations).toEqual([])
    expect(result.current.notifications).toEqual([])
    expect(api.profile).toHaveBeenCalledTimes(1)
    expect(api.pets).toHaveBeenCalledTimes(1)
    expect(api.reservations).toHaveBeenCalledTimes(1)
    expect(api.notifications).toHaveBeenCalledTimes(1)
  })

  it('surfaces a closed initial-load error and retries all resources', async () => {
    const api = fakeClientApi({ profile })
    api.profile
      .mockRejectedValueOnce(new ApiError('No pudimos cargar tu perfil.', {
        code: 'PROFILE_UNAVAILABLE',
        status: 503,
      }))
      .mockResolvedValueOnce(profile)
    const { result } = renderHook(() => useClientPanel(api))

    await waitUntilLoaded(result)
    expect(result.current.loadError).toBe('No pudimos cargar tu perfil.')

    await act(async () => {
      await result.current.retry()
    })

    expect(result.current.loadError).toBe('')
    expect(result.current.profile).toEqual(profile)
    expect(api.profile).toHaveBeenCalledTimes(2)
    expect(api.pets).toHaveBeenCalledTimes(2)
    expect(api.reservations).toHaveBeenCalledTimes(2)
    expect(api.notifications).toHaveBeenCalledTimes(2)
  })

  it('does not let a stale initial response replace a newer profile mutation', async () => {
    const oldProfile = { ...profile, name: 'Nombre anterior' }
    const newProfile = { ...profile, name: 'Nombre actualizado' }
    const initialProfile = deferred()
    const api = fakeClientApi({ profile: oldProfile, pets: [milo] })
    api.profile.mockReturnValueOnce(initialProfile.promise)
    api.updateProfile.mockResolvedValueOnce(newProfile)
    const { result } = renderHook(() => useClientPanel(api))

    await act(async () => {
      await result.current.updateProfile({
        name: 'Nombre actualizado',
        phone: profile.phone,
      })
    })
    expect(result.current.profile).toEqual(newProfile)

    await act(async () => {
      initialProfile.resolve(oldProfile)
      await initialProfile.promise
    })

    expect(result.current.profile).toEqual(newProfile)
    expect(result.current.pets).toEqual([milo])
    expect(result.current.loading).toBe(false)
  })

  it('keeps a completed profile update newer than a retry snapshot requested while the update was pending', async () => {
    const oldProfile = { ...profile, name: 'Nombre anterior' }
    const newProfile = { ...profile, name: 'Nombre actualizado' }
    const updateRequest = deferred()
    const retrySnapshot = deferred()
    const api = fakeClientApi({ profile: oldProfile })
    api.profile
      .mockResolvedValueOnce(oldProfile)
      .mockReturnValueOnce(retrySnapshot.promise)
    api.updateProfile.mockReturnValueOnce(updateRequest.promise)
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    let updatePromise
    let retryPromise
    act(() => {
      updatePromise = result.current.updateProfile({
        name: newProfile.name,
        phone: newProfile.phone,
      })
      retryPromise = result.current.retry()
    })

    await act(async () => {
      updateRequest.resolve(newProfile)
      await updatePromise
    })
    await act(async () => {
      retrySnapshot.resolve(oldProfile)
      await retryPromise
    })

    expect(result.current.profile).toEqual(newProfile)
  })

  it('reloads only pets after creation and blocks a concurrent duplicate', async () => {
    const luna = {
      id: 85,
      name: 'Luna',
      species: 'Perro',
      breed: null,
      birthdate: null,
      active: true,
    }
    const createRequest = deferred()
    const api = fakeClientApi({ profile, pets: [milo] })
    api.pets
      .mockResolvedValueOnce([milo])
      .mockResolvedValueOnce([milo, luna])
    api.createPet.mockReturnValueOnce(createRequest.promise)
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    let first
    let duplicate
    act(() => {
      first = result.current.createPet({
        name: 'Luna',
        species: 'Perro',
        breed: null,
        birthdate: null,
      })
      duplicate = result.current.createPet({
        name: 'Luna',
        species: 'Perro',
        breed: null,
        birthdate: null,
      })
    })
    await expect(duplicate).resolves.toBeNull()
    expect(api.createPet).toHaveBeenCalledTimes(1)

    await act(async () => {
      createRequest.resolve(luna)
      await first
    })

    expect(result.current.pets).toEqual([milo, luna])
    expect(api.pets).toHaveBeenCalledTimes(2)
    expect(api.profile).toHaveBeenCalledTimes(1)
    expect(api.reservations).toHaveBeenCalledTimes(1)
    expect(api.notifications).toHaveBeenCalledTimes(1)
  })

  it('preserves pets and releases the lock when an archive mutation fails', async () => {
    const api = fakeClientApi({ profile, pets: [milo] })
    api.archivePet.mockRejectedValueOnce(new ApiError(
      'Cancela o resuelve primero las reservas futuras de esta mascota.',
      { code: 'PET_HAS_FUTURE_RESERVATION', status: 409 },
    ))
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    await act(async () => {
      await expect(result.current.archivePet(milo.id)).rejects.toMatchObject({
        code: 'PET_HAS_FUTURE_RESERVATION',
      })
    })

    expect(result.current.pets).toEqual([milo])
    expect(result.current.isPending(`archivePet:${milo.id}`)).toBe(false)
    expect(api.pets).toHaveBeenCalledTimes(1)
  })

  it('reloads reservations after cancellation and exposes rescheduling without touching other data', async () => {
    const pending = {
      id: 301,
      startsAt: '2099-08-10T10:00:00-04:00',
      endsAt: '2099-08-10T10:30:00-04:00',
      status: 'PENDING',
      note: null,
      items: [{ petId: 84, petName: 'Milo', serviceId: 7, serviceName: 'Consulta general' }],
      createdAt: '2026-08-01T09:15:00-04:00',
    }
    const cancelled = { ...pending, status: 'CANCELLED' }
    const moved = { ...pending, startsAt: '2099-08-11T11:30:00-04:00' }
    const api = fakeClientApi({ profile, reservations: [pending] })
    api.reservations
      .mockResolvedValueOnce([pending])
      .mockResolvedValueOnce([cancelled])
      .mockResolvedValueOnce([moved])
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    await act(async () => {
      await result.current.cancelReservation(301, 'Cambio de planes')
    })
    expect(result.current.reservations[0].status).toBe('CANCELLED')

    await act(async () => {
      await result.current.rescheduleReservation(
        301,
        '2099-08-11T11:30:00-04:00',
      )
    })
    expect(result.current.reservations[0].startsAt).toBe(
      '2099-08-11T11:30:00-04:00',
    )
    expect(api.profile).toHaveBeenCalledTimes(1)
    expect(api.pets).toHaveBeenCalledTimes(1)
    expect(api.notifications).toHaveBeenCalledTimes(1)
  })

  it('reloads only notifications after individual and read-all mutations, including zero', async () => {
    const unread = {
      id: 901,
      type: 'APPOINTMENT_REMINDER',
      title: 'Recordatorio',
      body: 'Tu reserva se acerca.',
      reservationId: 301,
      createdAt: '2026-08-02T15:10:00Z',
      unread: true,
    }
    const read = { ...unread, unread: false }
    const api = fakeClientApi({ profile, notifications: [unread] })
    api.notifications
      .mockResolvedValueOnce([unread])
      .mockResolvedValueOnce([read])
      .mockResolvedValueOnce([read])
    api.readAllNotifications.mockResolvedValueOnce({ markedRead: 0 })
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    await act(async () => {
      await result.current.readNotification(901)
    })
    expect(result.current.notifications[0].unread).toBe(false)

    let response
    await act(async () => {
      response = await result.current.readAllNotifications()
    })
    expect(response).toEqual({ markedRead: 0 })
    expect(api.notifications).toHaveBeenCalledTimes(3)
    expect(api.profile).toHaveBeenCalledTimes(1)
    expect(api.pets).toHaveBeenCalledTimes(1)
    expect(api.reservations).toHaveBeenCalledTimes(1)
  })

  it('ignores resource responses that settle after unmount', async () => {
    const pendingProfile = deferred()
    const api = fakeClientApi({ profile })
    api.profile.mockReturnValueOnce(pendingProfile.promise)
    const { unmount } = renderHook(() => useClientPanel(api))

    unmount()
    await act(async () => {
      pendingProfile.resolve(profile)
      await pendingProfile.promise
    })

    expect(api.profile).toHaveBeenCalledTimes(1)
    expect(api.pets).toHaveBeenCalledTimes(1)
    expect(api.reservations).toHaveBeenCalledTimes(1)
    expect(api.notifications).toHaveBeenCalledTimes(1)
  })

  it('keeps a created pet and offers a reload-only retry when the follow-up GET fails', async () => {
    const luna = {
      id: 85,
      name: 'Luna',
      species: 'Perro',
      breed: null,
      birthdate: null,
      active: true,
    }
    const api = fakeClientApi({ profile, pets: [milo] })
    api.createPet.mockResolvedValueOnce(luna)
    api.pets
      .mockResolvedValueOnce([milo])
      .mockRejectedValueOnce(new ApiError('No pudimos actualizar tus mascotas.', {
        code: 'PETS_UNAVAILABLE',
        status: 503,
      }))
      .mockResolvedValueOnce([milo, luna])
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    let created
    await act(async () => {
      created = await result.current.createPet({
        name: 'Luna',
        species: 'Perro',
        breed: null,
        birthdate: null,
      })
    })

    expect(created).toEqual(luna)
    expect(result.current.pets).toEqual([milo, luna])
    expect(result.current.refreshFailures.pets).toMatchObject({
      message: 'No pudimos actualizar tus mascotas.',
    })

    await act(async () => {
      await result.current.retryRefresh('pets')
    })

    expect(result.current.refreshFailures.pets).toBeNull()
    expect(api.createPet).toHaveBeenCalledTimes(1)
    expect(api.pets).toHaveBeenCalledTimes(3)
  })

  it('keeps an authoritative pet update when its follow-up GET fails', async () => {
    const edited = { ...milo, breed: 'Europeo' }
    const api = fakeClientApi({ profile, pets: [milo] })
    api.updatePet.mockResolvedValueOnce(edited)
    api.pets
      .mockResolvedValueOnce([milo])
      .mockRejectedValueOnce(new ApiError('No pudimos recargar tus mascotas.', {
        code: 'PETS_UNAVAILABLE',
        status: 503,
      }))
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    await act(async () => {
      await result.current.updatePet(milo.id, {
        name: milo.name,
        species: milo.species,
        breed: edited.breed,
        birthdate: milo.birthdate,
      })
    })

    expect(result.current.pets).toEqual([edited])
    expect(result.current.refreshFailures.pets).toBeInstanceOf(ApiError)
    expect(api.updatePet).toHaveBeenCalledTimes(1)
  })

  it('removes an archived pet locally when its follow-up GET fails', async () => {
    const api = fakeClientApi({ profile, pets: [milo] })
    api.pets
      .mockResolvedValueOnce([milo])
      .mockRejectedValueOnce(new ApiError('No pudimos recargar tus mascotas.', {
        code: 'PETS_UNAVAILABLE',
        status: 503,
      }))
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    await act(async () => {
      await result.current.archivePet(milo.id)
    })

    expect(result.current.pets).toEqual([])
    expect(result.current.refreshFailures.pets).toBeInstanceOf(ApiError)
    expect(api.archivePet).toHaveBeenCalledTimes(1)
  })

  it('reconciles the cancellation DTO when its follow-up GET fails', async () => {
    const pending = {
      id: 301,
      startsAt: '2099-08-10T10:00:00-04:00',
      endsAt: '2099-08-10T10:30:00-04:00',
      status: 'PENDING',
      note: null,
      items: [{ petId: 84, petName: 'Milo', serviceId: 7, serviceName: 'Consulta general' }],
      createdAt: '2026-08-01T09:15:00-04:00',
    }
    const cancellation = {
      id: 301,
      status: 'CANCELLED',
      startsAt: pending.startsAt,
      cancelledAt: '2026-08-02T11:00:00-04:00',
      cancelledBy: 'CLIENT',
      reason: 'Cambio de planes',
      updatedAt: '2026-08-02T11:00:00-04:00',
    }
    const api = fakeClientApi({ profile, reservations: [pending] })
    api.cancelReservation.mockResolvedValueOnce(cancellation)
    api.reservations
      .mockResolvedValueOnce([pending])
      .mockRejectedValueOnce(new ApiError('No pudimos recargar tus reservas.', {
        code: 'RESERVATIONS_UNAVAILABLE',
        status: 503,
      }))
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    await act(async () => {
      await result.current.cancelReservation(301, 'Cambio de planes')
    })

    expect(result.current.reservations[0]).toMatchObject(cancellation)
    expect(result.current.reservations[0].items).toEqual(pending.items)
    expect(result.current.refreshFailures.reservations).toBeInstanceOf(ApiError)
    expect(api.cancelReservation).toHaveBeenCalledTimes(1)
  })

  it('reconciles the reschedule DTO when its follow-up GET fails', async () => {
    const pending = {
      id: 301,
      startsAt: '2099-08-10T10:00:00-04:00',
      endsAt: '2099-08-10T10:30:00-04:00',
      status: 'CONFIRMED',
      note: null,
      items: [{ petId: 84, petName: 'Milo', serviceId: 7, serviceName: 'Consulta general' }],
      createdAt: '2026-08-01T09:15:00-04:00',
    }
    const moved = {
      id: 301,
      previousStartsAt: pending.startsAt,
      startsAt: '2099-08-11T11:30:00-04:00',
      endsAt: '2099-08-11T12:00:00-04:00',
      previousStatus: 'CONFIRMED',
      status: 'PENDING',
      updatedAt: '2026-08-02T11:10:00-04:00',
    }
    const api = fakeClientApi({ profile, reservations: [pending] })
    api.rescheduleReservation.mockResolvedValueOnce(moved)
    api.reservations
      .mockResolvedValueOnce([pending])
      .mockRejectedValueOnce(new ApiError('No pudimos recargar tus reservas.', {
        code: 'RESERVATIONS_UNAVAILABLE',
        status: 503,
      }))
    const { result } = renderHook(() => useClientPanel(api))
    await waitUntilLoaded(result)

    await act(async () => {
      await result.current.rescheduleReservation(301, moved.startsAt)
    })

    expect(result.current.reservations[0]).toMatchObject({
      id: 301,
      startsAt: moved.startsAt,
      endsAt: moved.endsAt,
      status: 'PENDING',
      updatedAt: moved.updatedAt,
    })
    expect(result.current.reservations[0].items).toEqual(pending.items)
    expect(result.current.refreshFailures.reservations).toBeInstanceOf(ApiError)
    expect(api.rescheduleReservation).toHaveBeenCalledTimes(1)
  })
})

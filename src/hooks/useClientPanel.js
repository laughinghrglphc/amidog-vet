import { useCallback, useEffect, useRef, useState } from 'react'
import { clientApi } from '../api/clientApi'
import { ApiError } from '../api/http'

const RESOURCE_NAMES = ['profile', 'pets', 'reservations', 'notifications']
const INITIAL_LOAD_ERROR = 'No pudimos cargar tu panel. Intenta nuevamente.'

export function clientErrorFeedback(error, fallback) {
  if (error instanceof ApiError && error.message.trim()) {
    const fields = Object.values(error.errors ?? {})
      .filter((value) => typeof value === 'string' && value.trim())
    return { fields, message: error.message }
  }
  return { fields: [], message: fallback }
}

export function useClientPanel(api = clientApi) {
  const [profile, setProfile] = useState(null)
  const [pets, setPets] = useState([])
  const [reservations, setReservations] = useState([])
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [pendingKeys, setPendingKeys] = useState([])
  const [refreshFailures, setRefreshFailures] = useState({
    notifications: null,
    pets: null,
    profile: null,
    reservations: null,
  })
  const mountedRef = useRef(false)
  const startedApiRef = useRef(null)
  const batchGenerationRef = useRef(0)
  const resourceGenerationsRef = useRef({
    notifications: 0,
    pets: 0,
    profile: 0,
    reservations: 0,
  })
  const profileMutationGenerationRef = useRef(0)
  const pendingRef = useRef(new Set())

  const commitResource = useCallback((resource, value) => {
    if (resource === 'profile') setProfile(value)
    if (resource === 'pets') setPets(value)
    if (resource === 'reservations') setReservations(value)
    if (resource === 'notifications') setNotifications(value)
  }, [])

  const resourceRequest = useCallback((resource) => api[resource](), [api])

  const refreshResource = useCallback(async (resource) => {
    const generation = resourceGenerationsRef.current[resource] + 1
    resourceGenerationsRef.current[resource] = generation
    const value = await resourceRequest(resource)
    if (mountedRef.current
      && resourceGenerationsRef.current[resource] === generation) {
      commitResource(resource, value)
      setRefreshFailures((current) => (
        current[resource] === null
          ? current
          : { ...current, [resource]: null }
      ))
    }
    return value
  }, [commitResource, resourceRequest])

  const rememberRefreshFailure = useCallback((resource, error) => {
    if (!mountedRef.current || startedApiRef.current !== api) return
    setRefreshFailures((current) => ({ ...current, [resource]: error }))
  }, [api])

  const refreshAfterMutation = useCallback(async (resource) => {
    try {
      await refreshResource(resource)
    } catch (error) {
      rememberRefreshFailure(resource, error)
    }
  }, [refreshResource, rememberRefreshFailure])

  const refreshAll = useCallback(async () => {
    const batchGeneration = batchGenerationRef.current + 1
    batchGenerationRef.current = batchGeneration
    const resourceGenerations = Object.fromEntries(
      RESOURCE_NAMES.map((resource) => {
        const generation = resourceGenerationsRef.current[resource] + 1
        resourceGenerationsRef.current[resource] = generation
        return [resource, generation]
      }),
    )

    if (mountedRef.current) {
      setLoading(true)
      setLoadError('')
    }

    try {
      const results = await Promise.allSettled(
        RESOURCE_NAMES.map((resource) => Promise.resolve()
          .then(() => resourceRequest(resource))),
      )
      if (!mountedRef.current || batchGenerationRef.current !== batchGeneration) {
        return false
      }
      let currentError = null
      RESOURCE_NAMES.forEach((resource, index) => {
        if (resourceGenerationsRef.current[resource] !== resourceGenerations[resource]) {
          return
        }
        const result = results[index]
        if (result.status === 'fulfilled') {
          commitResource(resource, result.value)
        } else if (!currentError) {
          currentError = result.reason
        }
      })
      setLoadError(currentError
        ? clientErrorFeedback(currentError, INITIAL_LOAD_ERROR).message
        : '')
      return currentError === null
    } finally {
      if (mountedRef.current && batchGenerationRef.current === batchGeneration) {
        setLoading(false)
      }
    }
  }, [commitResource, resourceRequest])

  useEffect(() => {
    mountedRef.current = true
    if (startedApiRef.current !== api) {
      startedApiRef.current = api
      void refreshAll()
    }
    return () => {
      mountedRef.current = false
    }
  }, [api, refreshAll])

  const runMutation = useCallback(async (key, resource, operation) => {
    if (pendingRef.current.has(key)) {
      return null
    }
    pendingRef.current.add(key)
    resourceGenerationsRef.current[resource] += 1
    if (mountedRef.current) {
      setPendingKeys(Array.from(pendingRef.current))
    }
    try {
      return await operation()
    } finally {
      pendingRef.current.delete(key)
      if (mountedRef.current) {
        setPendingKeys(Array.from(pendingRef.current))
      }
    }
  }, [])

  const updateProfile = useCallback((body) => runMutation(
    'updateProfile',
    'profile',
    async () => {
      const mutationGeneration = profileMutationGenerationRef.current + 1
      profileMutationGenerationRef.current = mutationGeneration
      const updated = await api.updateProfile(body)
      if (mountedRef.current
        && startedApiRef.current === api
        && profileMutationGenerationRef.current === mutationGeneration) {
        resourceGenerationsRef.current.profile += 1
        setProfile(updated)
      }
      return updated
    },
  ), [api, runMutation])

  const createPet = useCallback((body) => runMutation(
    'createPet',
    'pets',
    async () => {
      const created = await api.createPet(body)
      if (mountedRef.current && startedApiRef.current === api) {
        setPets((current) => (
          current.some((pet) => pet.id === created.id)
            ? current.map((pet) => pet.id === created.id ? created : pet)
            : [...current, created]
        ))
      }
      await refreshAfterMutation('pets')
      return created
    },
  ), [api, refreshAfterMutation, runMutation])

  const updatePet = useCallback((id, body) => runMutation(
    `updatePet:${id}`,
    'pets',
    async () => {
      const updated = await api.updatePet(id, body)
      if (mountedRef.current && startedApiRef.current === api) {
        setPets((current) => current.map((pet) => (
          pet.id === updated.id ? updated : pet
        )))
      }
      await refreshAfterMutation('pets')
      return updated
    },
  ), [api, refreshAfterMutation, runMutation])

  const archivePet = useCallback((id) => runMutation(
    `archivePet:${id}`,
    'pets',
    async () => {
      await api.archivePet(id)
      if (mountedRef.current && startedApiRef.current === api) {
        setPets((current) => current.filter((pet) => pet.id !== id))
      }
      await refreshAfterMutation('pets')
      return true
    },
  ), [api, refreshAfterMutation, runMutation])

  const cancelReservation = useCallback((id, reason) => runMutation(
    `cancelReservation:${id}`,
    'reservations',
    async () => {
      const cancellation = await api.cancelReservation(id, reason)
      if (mountedRef.current && startedApiRef.current === api) {
        setReservations((current) => current.map((reservation) => (
          reservation.id === cancellation.id
            ? { ...reservation, ...cancellation }
            : reservation
        )))
      }
      await refreshAfterMutation('reservations')
      return cancellation
    },
  ), [api, refreshAfterMutation, runMutation])

  const rescheduleReservation = useCallback((id, startsAt) => runMutation(
    `rescheduleReservation:${id}`,
    'reservations',
    async () => {
      const rescheduled = await api.rescheduleReservation(id, startsAt)
      if (mountedRef.current && startedApiRef.current === api) {
        setReservations((current) => current.map((reservation) => (
          reservation.id === rescheduled.id
            ? {
                ...reservation,
                startsAt: rescheduled.startsAt,
                endsAt: rescheduled.endsAt,
                status: rescheduled.status,
                updatedAt: rescheduled.updatedAt,
              }
            : reservation
        )))
      }
      await refreshAfterMutation('reservations')
      return rescheduled
    },
  ), [api, refreshAfterMutation, runMutation])

  const readNotification = useCallback((id) => runMutation(
    `readNotification:${id}`,
    'notifications',
    async () => {
      await api.readNotification(id)
      await refreshResource('notifications')
      return true
    },
  ), [api, refreshResource, runMutation])

  const readAllNotifications = useCallback(() => runMutation(
    'readAllNotifications',
    'notifications',
    async () => {
      const result = await api.readAllNotifications()
      await refreshResource('notifications')
      return result
    },
  ), [api, refreshResource, runMutation])

  const isPending = useCallback(
    (key) => pendingKeys.includes(key),
    [pendingKeys],
  )

  return {
    archivePet,
    cancelReservation,
    createPet,
    isPending,
    loadError,
    loading,
    notifications,
    pets,
    profile,
    refreshFailures,
    readAllNotifications,
    readNotification,
    reservations,
    rescheduleReservation,
    retryRefresh: refreshResource,
    retry: refreshAll,
    updatePet,
    updateProfile,
  }
}

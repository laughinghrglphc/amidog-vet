import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'

export default function useAdminNotifications(api, pollIntervalMs = 30_000) {
  const [data, setDataState] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const apiRef = useRef(api)
  const dataRef = useRef(null)
  const disposedRef = useRef(false)
  const generationRef = useRef(0)
  const inFlightRef = useRef(null)

  useEffect(() => {
    apiRef.current = api
  }, [api])

  const setData = useCallback((value) => {
    setDataState((current) => {
      const next = typeof value === 'function' ? value(current) : value
      dataRef.current = next
      return next
    })
  }, [])

  const reload = useCallback(() => {
    if (disposedRef.current) return Promise.resolve({ status: 'disposed' })
    if (inFlightRef.current) return inFlightRef.current

    const generation = ++generationRef.current
    if (!dataRef.current) setLoading(true)
    setError(null)

    const request = Promise.resolve()
      .then(() => apiRef.current.notifications())
      .then((value) => {
        if (disposedRef.current) return { status: 'disposed' }
        if (generation !== generationRef.current) return { status: 'stale' }
        setData(Array.isArray(value) ? value : [])
        return { status: 'success', value }
      })
      .catch((nextError) => {
        if (disposedRef.current) return { status: 'disposed' }
        if (generation !== generationRef.current) return { status: 'stale' }
        setError(nextError)
        return { status: 'failure', error: nextError }
      })
      .finally(() => {
        if (inFlightRef.current === request) inFlightRef.current = null
        if (!disposedRef.current && generation === generationRef.current) {
          setLoading(false)
        }
      })

    inFlightRef.current = request
    return request
  }, [setData])

  useEffect(() => {
    disposedRef.current = false
    void reload()
    return () => {
      disposedRef.current = true
      generationRef.current += 1
      inFlightRef.current = null
    }
  }, [reload])

  useEffect(() => {
    const refreshWhenVisible = () => {
      if (document.visibilityState !== 'hidden') void reload()
    }
    const handleVisibilityChange = () => {
      if (document.visibilityState !== 'hidden') void reload()
    }
    const intervalId = window.setInterval(refreshWhenVisible, pollIntervalMs)

    window.addEventListener('focus', refreshWhenVisible)
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      window.clearInterval(intervalId)
      window.removeEventListener('focus', refreshWhenVisible)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [pollIntervalMs, reload])

  const unreadCount = useMemo(
    () => data?.filter((notification) => notification.unread).length ?? 0,
    [data],
  )

  return {
    data,
    error,
    loading,
    reload,
    setData,
    unreadCount,
  }
}

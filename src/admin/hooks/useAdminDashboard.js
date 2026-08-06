import { useCallback, useEffect, useRef, useState } from 'react'

export default function useAdminDashboard(api) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const generationRef = useRef(0)

  const load = useCallback(() => {
    const generation = ++generationRef.current

    return Promise.resolve().then(async () => {
      if (generation !== generationRef.current) return { status: 'stale' }
      setLoading(true)
      setError(null)

      try {
        const dashboard = await api.dashboard()
        if (generation !== generationRef.current) return { status: 'stale' }
        setData(dashboard)
        return { status: 'success', value: dashboard }
      } catch (nextError) {
        if (generation !== generationRef.current) return { status: 'stale' }
        setError(nextError)
        return { status: 'failure', error: nextError }
      } finally {
        if (generation === generationRef.current) setLoading(false)
      }
    })
  }, [api])

  useEffect(() => {
    void load()
    return () => {
      generationRef.current += 1
    }
  }, [load])

  return {
    data,
    error,
    loading,
    reload: load,
  }
}

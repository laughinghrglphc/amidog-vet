import { useCallback, useEffect, useRef, useState } from 'react'

const objectIds = new WeakMap()
let nextObjectId = 1

function dependencyKey(dependencies) {
  return dependencies.map((dependency) => {
    if (
      (typeof dependency === 'object' && dependency !== null)
      || typeof dependency === 'function'
    ) {
      if (!objectIds.has(dependency)) objectIds.set(dependency, nextObjectId++)
      return `object:${objectIds.get(dependency)}`
    }
    return `${typeof dependency}:${String(dependency)}`
  }).join('|')
}

export default function useManagementResource(loader, dependencies = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const generationRef = useRef(0)
  const loaderRef = useRef(loader)
  const disposedRef = useRef(false)
  const reloadKey = dependencyKey(dependencies)

  useEffect(() => {
    loaderRef.current = loader
  }, [loader])

  const reload = useCallback(() => {
    if (disposedRef.current) return Promise.resolve({ status: 'disposed' })
    const generation = ++generationRef.current
    setLoading(true)
    setError(null)
    return Promise.resolve()
      .then(async () => {
        try {
          const value = await loaderRef.current()
          if (disposedRef.current) return { status: 'disposed' }
          if (generation !== generationRef.current) {
            return { status: 'stale' }
          }
          setData(value)
          return { status: 'success', value }
        } catch (nextError) {
          if (disposedRef.current) return { status: 'disposed' }
          if (generation !== generationRef.current) {
            return { status: 'stale' }
          }
          setError(nextError)
          return { status: 'failure', error: nextError }
        } finally {
          if (!disposedRef.current && generation === generationRef.current) {
            setLoading(false)
          }
        }
      })
  }, [])

  useEffect(() => {
    disposedRef.current = false
    return () => {
      disposedRef.current = true
      generationRef.current += 1
    }
  }, [])

  useEffect(() => {
    void reload()
    return () => {
      generationRef.current += 1
    }
  }, [reload, reloadKey])

  return { data, error, loading, reload, setData }
}

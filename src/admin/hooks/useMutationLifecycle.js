import { useCallback, useEffect, useRef, useState } from 'react'

function findReloadFailure(result) {
  if (Array.isArray(result)) {
    return result.map(findReloadFailure).find(Boolean) ?? null
  }
  if (result?.status === 'failure') return result.error
  return null
}

function isInactiveReload(result) {
  if (Array.isArray(result)) return result.some(isInactiveReload)
  return ['disposed', 'stale'].includes(result?.status)
}

export default function useMutationLifecycle() {
  const [pendingKeys, setPendingKeys] = useState(() => new Set())
  const attemptRef = useRef(0)
  const disposedRef = useRef(false)
  const pendingRef = useRef(new Set())

  useEffect(() => {
    disposedRef.current = false
    return () => {
      disposedRef.current = true
      attemptRef.current += 1
    }
  }, [])

  const run = useCallback((key, steps) => {
    if (disposedRef.current || pendingRef.current.size > 0) {
      return Promise.resolve(false)
    }

    const attempt = ++attemptRef.current
    pendingRef.current.add(key)
    setPendingKeys(new Set(pendingRef.current))
    steps.begin?.()

    const isCurrent = () =>
      !disposedRef.current && attempt === attemptRef.current

    let request
    try {
      request = steps.request()
    } catch (error) {
      if (isCurrent()) steps.failure?.(error)
      pendingRef.current.clear()
      setPendingKeys(new Set())
      return Promise.resolve(false)
    }

    return Promise.resolve(request)
      .then(async (result) => {
        if (!isCurrent()) return false
        steps.reconcile?.(result)
        const reloadResult = await steps.reload?.(result)
        if (!isCurrent()) return false
        if (isInactiveReload(reloadResult)) return false
        const refreshError = findReloadFailure(reloadResult)
        if (refreshError) {
          const savedError = new Error(
            'El cambio se guardó, pero no pudimos actualizar los datos.',
            { cause: refreshError },
          )
          if (steps.refreshFailure) {
            steps.refreshFailure(savedError, result)
          } else {
            steps.failure?.(savedError)
          }
          return true
        }
        steps.success?.(result)
        return true
      })
      .catch((error) => {
        if (isCurrent()) steps.failure?.(error)
        return false
      })
      .finally(() => {
        if (!isCurrent()) return
        pendingRef.current.clear()
        setPendingKeys(new Set())
      })
  }, [])

  const isPending = useCallback(
    (key) => pendingKeys.has(key),
    [pendingKeys],
  )

  return {
    anyPending: pendingKeys.size > 0,
    isPending,
    run,
  }
}

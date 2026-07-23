import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { authApi } from '../api/authApi'
import { clearPersistedBookingDraft } from '../hooks/useBookingDraft'
import { AuthContext } from './authContext'

function isAnonymousError(error) {
  return error?.status === 401
}

export function AuthProvider({ api = authApi, children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const mountedRef = useRef(false)
  const initialLoadStartedRef = useRef(false)
  const operationRef = useRef(0)

  const refresh = useCallback(async () => {
    const operation = ++operationRef.current
    setLoading(true)
    setError(null)

    try {
      const currentUser = await api.me()
      if (mountedRef.current && operation === operationRef.current) {
        setUser(currentUser ?? null)
        setLoading(false)
      }
      return operation === operationRef.current ? currentUser ?? null : null
    } catch (requestError) {
      if (mountedRef.current && operation === operationRef.current) {
        setUser(null)
        setError(isAnonymousError(requestError) ? null : requestError)
        setLoading(false)
      }
      return null
    }
  }, [api])

  const login = useCallback(async (email, password) => {
    const operation = ++operationRef.current
    setError(null)
    try {
      const authenticatedUser = await api.login(email, password)
      if (mountedRef.current && operation === operationRef.current) {
        clearPersistedBookingDraft()
        setUser(authenticatedUser)
        setLoading(false)
        return authenticatedUser
      }
      return null
    } catch (requestError) {
      if (mountedRef.current && operation === operationRef.current) {
        setLoading(false)
      }
      throw requestError
    }
  }, [api])

  const logout = useCallback(async () => {
    const operation = ++operationRef.current
    setError(null)
    try {
      await api.logout()
      if (mountedRef.current && operation === operationRef.current) {
        clearPersistedBookingDraft()
        setUser(null)
        setLoading(false)
        return true
      }
      return false
    } catch (requestError) {
      if (mountedRef.current && operation === operationRef.current) {
        setLoading(false)
      }
      throw requestError
    }
  }, [api])

  useEffect(() => {
    mountedRef.current = true

    const handleUnauthorized = () => {
      operationRef.current += 1
      setUser(null)
      setError(null)
      setLoading(false)
    }

    window.addEventListener('amidog:unauthorized', handleUnauthorized)
    if (!initialLoadStartedRef.current) {
      initialLoadStartedRef.current = true
      void refresh()
    }

    return () => {
      mountedRef.current = false
      window.removeEventListener('amidog:unauthorized', handleUnauthorized)
    }
  }, [refresh])

  const value = useMemo(() => ({
    error,
    loading,
    login,
    logout,
    refresh,
    retry: refresh,
    user,
  }), [error, loading, login, logout, refresh, user])

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

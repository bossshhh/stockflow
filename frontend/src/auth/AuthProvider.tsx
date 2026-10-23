import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { api, unwrap } from '@/api/client'
import { AuthContext, type AuthState, type User } from './context'
import { refreshAccessToken } from './refresh'
import { onAccessTokenChange, setAccessToken } from './token-store'


async function loadCurrentUser(): Promise<User> {
  return unwrap(api.GET('/api/v1/auth/me'))
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  // Restore the session from the HttpOnly refresh cookie on page load.
  useEffect(() => {
    let cancelled = false
    refreshAccessToken()
      .then(async (token) => (token === null ? null : loadCurrentUser()))
      .then((user) => {
        if (!cancelled) setState(user === null ? { status: 'anonymous' } : { status: 'authenticated', user })
      })
      .catch(() => {
        if (!cancelled) setState({ status: 'anonymous' })
      })
    return () => {
      cancelled = true
    }
  }, [])

  // If a refresh fails later (session revoked or expired), drop to signed-out everywhere.
  useEffect(
    () =>
      onAccessTokenChange((token) => {
        if (token === null) {
          setState((current) => (current.status === 'authenticated' ? { status: 'anonymous' } : current))
          queryClient.clear()
        }
      }),
    [queryClient],
  )

  const signIn = useCallback(async (email: string, password: string) => {
    const tokens = await unwrap(api.POST('/api/v1/auth/login', { body: { email, password } }))
    setAccessToken(tokens.accessToken)
    const user = await loadCurrentUser()
    setState({ status: 'authenticated', user })
    return user
  }, [])

  const signOut = useCallback(async () => {
    try {
      await unwrap(api.POST('/api/v1/auth/logout'))
    } finally {
      // Even if the server is unreachable, forget the session locally.
      setAccessToken(null)
      setState({ status: 'anonymous' })
      queryClient.clear()
    }
  }, [queryClient])

  const value = useMemo(() => ({ state, signIn, signOut }), [state, signIn, signOut])
  return <AuthContext value={value}>{children}</AuthContext>
}

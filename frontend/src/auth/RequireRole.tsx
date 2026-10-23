import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { FullPageSpinner } from '@/components/states'
import type { Role } from './context'
import { useAuth } from './useAuth'

/**
 * Route guard. This is convenience, not security: the backend enforces every permission. It only keeps
 * people from landing on pages whose API calls would all answer 401/403.
 */
export function RequireRole({ roles, children }: { roles: readonly Role[]; children: ReactNode }) {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') {
    return <FullPageSpinner />
  }
  if (state.status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  if (!roles.includes(state.user.role)) {
    return <Navigate to="/" replace />
  }
  return children
}

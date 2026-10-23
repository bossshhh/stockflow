import { use } from 'react'

import { AuthContext, type AuthContextValue, type Role, type User } from './context'

export function useAuth(): AuthContextValue {
  const context = use(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}

/** The signed-in user, or null while loading / signed out. */
export function useCurrentUser(): User | null {
  const { state } = useAuth()
  return state.status === 'authenticated' ? state.user : null
}

export const STAFF_ROLES: readonly Role[] = ['STAFF', 'ADMIN']

export function isStaff(user: User | null): boolean {
  return user !== null && STAFF_ROLES.includes(user.role)
}

/** Who may pack, ship and deliver (mirrors SecurityConfig). */
export const FULFILMENT_ROLES: readonly Role[] = ['WAREHOUSE', 'STAFF', 'ADMIN']

export function canFulfil(user: User | null): boolean {
  return user !== null && FULFILMENT_ROLES.includes(user.role)
}

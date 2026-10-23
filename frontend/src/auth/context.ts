import { createContext } from 'react'

import type { components } from '@/api/schema'

export type User = components['schemas']['UserResponse']
export type Role = User['role']

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: User }

export interface AuthContextValue {
  state: AuthState
  signIn: (email: string, password: string) => Promise<User>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

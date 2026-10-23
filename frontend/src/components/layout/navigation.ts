import {
  Boxes,
  ClipboardList,
  LayoutDashboard,
  Package,
  ShoppingCart,
  Store,
  Users,
  type LucideIcon,
} from 'lucide-react'

import type { User } from '@/auth/context'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

const SHOP: NavItem = { to: '/shop', label: 'Shop', icon: Store }

/** What each kind of user can reach. Mirrors the backend's role rules (which are the real enforcement). */
export function navigationFor(user: User | null): NavItem[] {
  if (user === null) {
    return [SHOP]
  }
  switch (user.role) {
    case 'CUSTOMER':
      return [
        SHOP,
        { to: '/cart', label: 'Cart', icon: ShoppingCart },
        { to: '/orders', label: 'My orders', icon: ClipboardList },
      ]
    case 'WAREHOUSE':
      return [
        { to: '/orders', label: 'Fulfilment', icon: ClipboardList },
        { to: '/inventory', label: 'Stock', icon: Boxes },
      ]
    case 'STAFF':
      return staffItems()
    case 'ADMIN':
      return [...staffItems(), { to: '/team', label: 'Team', icon: Users }]
  }
}

function staffItems(): NavItem[] {
  return [
    { to: '/dashboard', label: 'Overview', icon: LayoutDashboard },
    { to: '/orders', label: 'Orders', icon: ClipboardList },
    { to: '/inventory', label: 'Inventory', icon: Boxes },
    { to: '/products', label: 'Products', icon: Package },
    SHOP,
  ]
}

/** Where "/" sends each kind of user. */
export function homePathFor(user: User | null): string {
  if (user === null) return '/shop'
  switch (user.role) {
    case 'STAFF':
    case 'ADMIN':
      return '/dashboard'
    case 'WAREHOUSE':
      return '/orders'
    case 'CUSTOMER':
      return '/shop'
  }
}

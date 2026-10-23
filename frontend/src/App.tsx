import { lazy, type ComponentType } from 'react'
import { Navigate, Route, Routes } from 'react-router'

import type { Role } from '@/auth/context'
import { RequireRole } from '@/auth/RequireRole'
import { useAuth } from '@/auth/useAuth'
import { AppShell } from '@/components/layout/AppShell'
import { homePathFor } from '@/components/layout/navigation'
import { EmptyState, FullPageSpinner } from '@/components/states'

// Each page is its own chunk: customers never download the staff dashboard (and its chart library).
const LoginPage = lazyPage(() => import('@/features/auth/LoginPage'), 'LoginPage')
const RegisterPage = lazyPage(() => import('@/features/auth/RegisterPage'), 'RegisterPage')
const CartPage = lazyPage(() => import('@/features/cart/CartPage'), 'CartPage')
const DashboardPage = lazyPage(() => import('@/features/dashboard/DashboardPage'), 'DashboardPage')
const InventoryPage = lazyPage(() => import('@/features/inventory/InventoryPage'), 'InventoryPage')
const OrderDetailPage = lazyPage(() => import('@/features/orders/OrderDetailPage'), 'OrderDetailPage')
const OrdersPage = lazyPage(() => import('@/features/orders/OrdersPage'), 'OrdersPage')
const ProductsPage = lazyPage(() => import('@/features/products/ProductsPage'), 'ProductsPage')
const ShopPage = lazyPage(() => import('@/features/shop/ShopPage'), 'ShopPage')
const TeamPage = lazyPage(() => import('@/features/team/TeamPage'), 'TeamPage')

function lazyPage<K extends string>(load: () => Promise<Record<K, ComponentType>>, name: K) {
  return lazy(async () => ({ default: (await load())[name] }))
}

// Every role, spelled out once and checked by the compiler: adding a role to the API without adding it
// here fails to type-check (a hand-written shorter list once locked WAREHOUSE out of its order pages).
const ANY_SIGNED_IN = Object.keys({
  CUSTOMER: true, WAREHOUSE: true, STAFF: true, ADMIN: true,
} satisfies Record<Role, true>) as Role[]
const STAFF = ['STAFF', 'ADMIN'] as const
const STOCK_READERS = ['WAREHOUSE', 'STAFF', 'ADMIN'] as const

export function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Home />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="shop" element={<ShopPage />} />
        <Route path="cart" element={<RequireRole roles={['CUSTOMER']}><CartPage /></RequireRole>} />
        <Route path="orders" element={<RequireRole roles={ANY_SIGNED_IN}><OrdersPage /></RequireRole>} />
        <Route path="orders/:orderId" element={<RequireRole roles={ANY_SIGNED_IN}><OrderDetailPage /></RequireRole>} />
        <Route path="dashboard" element={<RequireRole roles={STAFF}><DashboardPage /></RequireRole>} />
        <Route path="inventory" element={<RequireRole roles={STOCK_READERS}><InventoryPage /></RequireRole>} />
        <Route path="products" element={<RequireRole roles={STAFF}><ProductsPage /></RequireRole>} />
        <Route path="team" element={<RequireRole roles={['ADMIN']}><TeamPage /></RequireRole>} />
        <Route path="*" element={<EmptyState title="This page does not exist">Use the menu to find your way.</EmptyState>} />
      </Route>
    </Routes>
  )
}

function Home() {
  const { state } = useAuth()
  if (state.status === 'loading') return <FullPageSpinner />
  return <Navigate to={homePathFor(state.status === 'authenticated' ? state.user : null)} replace />
}

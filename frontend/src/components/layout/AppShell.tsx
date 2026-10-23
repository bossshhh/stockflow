import { LogOut, Menu, ShoppingCart, UserRound } from 'lucide-react'
import { Suspense, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { toast } from 'sonner'

import { useAuth, useCurrentUser } from '@/auth/useAuth'
import { cartCount, useCart } from '@/cart/cart-store'
import { InlineSpinner } from '@/components/states'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { navigationFor, type NavItem } from './navigation'

export function AppShell() {
  const user = useCurrentUser()
  const items = navigationFor(user)

  return (
    <div className="min-h-dvh md:pl-16">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-16 flex-col items-center gap-2 border-r border-sidebar-border bg-sidebar py-4 md:flex">
        <Link to="/" aria-label="StockFlow home" className="mb-4 grid size-10 place-items-center rounded-lg bg-primary text-primary-foreground">
          <Wordmark compact />
        </Link>
        <nav aria-label="Main" className="flex flex-col gap-1">
          {items.map((item) => <RailLink key={item.to} item={item} />)}
        </nav>
      </aside>

      <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-background/90 px-4 backdrop-blur md:px-8">
        <MobileMenu items={items} />
        <Link to="/" className="font-heading text-xl font-semibold tracking-tight">
          <Wordmark />
        </Link>
        <div className="ml-auto flex items-center gap-2">
          {user?.role === 'CUSTOMER' && <CartButton />}
          {user === null ? (
            <Button asChild size="sm"><Link to="/login">Sign in</Link></Button>
          ) : (
            <UserMenu />
          )}
        </div>
      </header>

      <main className="mx-auto w-full max-w-7xl px-4 py-6 md:px-8 md:py-8">
        {/* Pages load lazily; only this area waits, the shell and menu stay put. */}
        <Suspense fallback={<InlineSpinner />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}

function Wordmark({ compact = false }: { compact?: boolean }) {
  if (compact) {
    return <span className="font-heading text-lg font-semibold leading-none">SF</span>
  }
  return (
    <span>
      Stock<span className="text-primary">Flow</span>
    </span>
  )
}

function RailLink({ item }: { item: NavItem }) {
  const Icon = item.icon
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <NavLink
          to={item.to}
          aria-label={item.label}
          className={({ isActive }) => cn(
            'grid size-10 place-items-center rounded-lg text-sidebar-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
            isActive && 'bg-sidebar-accent text-primary shadow-[inset_2px_0_0_var(--amber)]',
          )}
        >
          <Icon className="size-5" />
        </NavLink>
      </TooltipTrigger>
      <TooltipContent side="right">{item.label}</TooltipContent>
    </Tooltip>
  )
}

function MobileMenu({ items }: { items: NavItem[] }) {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" className="md:hidden" aria-label="Open menu">
          <Menu className="size-5" />
        </Button>
      </SheetTrigger>
      <SheetContent side="left" className="w-64">
        <SheetHeader>
          <SheetTitle><Wordmark /></SheetTitle>
        </SheetHeader>
        <nav aria-label="Main" className="flex flex-col gap-1 px-2">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => setOpen(false)}
              className={({ isActive }) => cn(
                'flex items-center gap-3 rounded-lg px-3 py-2 text-sm hover:bg-accent',
                isActive && 'bg-accent text-primary',
              )}
            >
              <item.icon className="size-4" /> {item.label}
            </NavLink>
          ))}
        </nav>
      </SheetContent>
    </Sheet>
  )
}

function CartButton() {
  const count = cartCount(useCart())
  return (
    <Button asChild variant="ghost" size="sm" aria-label={`Cart, ${count} items`}>
      <Link to="/cart">
        <ShoppingCart className="size-4" />
        <span className="tabular-nums">{count}</span>
      </Link>
    </Button>
  )
}

const ROLE_LABEL = { CUSTOMER: 'Customer', WAREHOUSE: 'Warehouse', STAFF: 'Staff', ADMIN: 'Admin' } as const

function UserMenu() {
  const { signOut } = useAuth()
  const user = useCurrentUser()
  const navigate = useNavigate()
  if (user === null) return null

  const handleSignOut = async () => {
    try {
      await signOut()
      toast.success('Signed out')
    } finally {
      navigate('/shop')
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-2">
          <UserRound className="size-4" />
          <span className="hidden max-w-40 truncate sm:inline">{user.fullName}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="font-normal">
          <p className="truncate text-sm">{user.email}</p>
          <p className="text-xs text-muted-foreground">{ROLE_LABEL[user.role]}</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={handleSignOut}>
          <LogOut className="size-4" /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

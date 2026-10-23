import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { api, unwrap } from '@/api/client'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState, ErrorState, InlineSpinner } from '@/components/states'
import { formatCount, formatDateTime, formatMoney, formatMoneyShort, orderReference } from '@/lib/format'
import { cn } from '@/lib/utils'
import { StockChart, type StockBar } from './StockChart'

const LOW_STOCK_THRESHOLD = 5
const CHART_PRODUCTS = 10
const DAY_MS = 24 * 60 * 60 * 1000

/** Start of today and of the window 7 days back, in the browser's time zone, as ISO instants. */
function dayWindows() {
  const startOfToday = new Date()
  startOfToday.setHours(0, 0, 0, 0)
  const tomorrow = new Date(startOfToday.getTime() + DAY_MS)
  const weekAgo = new Date(startOfToday.getTime() - 6 * DAY_MS)
  return { today: startOfToday.toISOString(), tomorrow: tomorrow.toISOString(), weekAgo: weekAgo.toISOString() }
}

export function DashboardPage() {
  const windows = dayWindows()

  const today = useQuery({
    queryKey: ['dashboard', 'summary', 'today', windows.today],
    queryFn: () => unwrap(api.GET('/api/v1/orders/summary', { params: { query: { from: windows.today, to: windows.tomorrow } } })),
  })
  const week = useQuery({
    queryKey: ['dashboard', 'summary', 'week', windows.weekAgo],
    queryFn: () => unwrap(api.GET('/api/v1/orders/summary', { params: { query: { from: windows.weekAgo, to: windows.tomorrow } } })),
  })
  const valuation = useQuery({
    queryKey: ['dashboard', 'valuation'],
    queryFn: () => unwrap(api.GET('/api/v1/inventory/valuation')),
  })

  return (
    <>
      <PageHeader title="Overview" description="Today on the warehouse floor." />

      <section aria-label="Key figures" className="mb-6 grid gap-px overflow-hidden rounded-xl border border-border bg-border sm:grid-cols-2 lg:grid-cols-4">
        <Figure label="Orders today" query={today} value={(data) => formatCount(data.totalOrders)} />
        <Figure
          label="Waiting for confirmation"
          query={today}
          value={(data) => formatCount(data.ordersByStatus.PENDING ?? 0)}
          emphasis
        />
        <Figure label="Confirmed revenue, last 7 days" query={week} value={(data) => formatMoneyShort(data.confirmedRevenue)}
          exact={(data) => formatMoney(data.confirmedRevenue)} />
        <Figure label="Stock value at current prices" query={valuation} value={(data) => formatMoneyShort(data.totalValue)}
          exact={(data) => formatMoney(data.totalValue)} />
      </section>

      <div className="grid grid-cols-[minmax(0,1fr)] items-start gap-6 xl:grid-cols-[minmax(0,1fr)_24rem]">
        <Panel title="Stock by product" link={{ to: '/inventory', label: 'All inventory' }}>
          <StockPanel />
        </Panel>
        <div className="grid content-start gap-6">
          <Panel title="Confirmation queue" link={{ to: '/orders', label: 'All orders' }}>
            <PendingQueue />
          </Panel>
          <Panel title={`Running low (${LOW_STOCK_THRESHOLD} or fewer available)`} link={{ to: '/inventory', label: 'Restock' }}>
            <LowStock />
          </Panel>
        </div>
      </div>
    </>
  )
}

interface QueryLike<T> {
  isPending: boolean
  isError: boolean
  data: T | undefined
}

function Figure<T>({ label, query, value, exact, emphasis = false }: {
  label: string
  query: QueryLike<T>
  value: (data: T) => string
  /** The full figure, shown on hover when `value` is abbreviated. */
  exact?: (data: T) => string
  emphasis?: boolean
}) {
  const ready = !query.isPending && !query.isError && query.data !== undefined
  return (
    <div className="min-w-0 bg-panel px-5 py-4">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p
        className={cn('mt-1 truncate font-heading text-3xl font-semibold tabular-nums', emphasis && 'text-primary')}
        title={ready && exact !== undefined && query.data !== undefined ? exact(query.data) : undefined}
      >
        {query.isPending ? '…' : query.isError || query.data === undefined ? '—' : value(query.data)}
      </p>
    </div>
  )
}

function Panel({ title, link, children }: { title: string; link: { to: string; label: string }; children: ReactNode }) {
  return (
    <section aria-label={title} className="rounded-xl border border-border bg-panel p-5">
      <div className="mb-4 flex items-baseline justify-between gap-4">
        <h2 className="font-heading text-xl">{title}</h2>
        <Link to={link.to} className="text-sm text-primary underline-offset-4 hover:underline">{link.label}</Link>
      </div>
      {children}
    </section>
  )
}

function StockPanel() {
  const stock = useQuery({
    queryKey: ['dashboard', 'stock'],
    queryFn: () => unwrap(api.GET('/api/v1/inventory', { params: { query: { page: 0, size: 100 } } })),
  })
  if (stock.isPending) return <InlineSpinner label="Loading stock" />
  if (stock.isError) return <ErrorState error={stock.error} onRetry={() => stock.refetch()} />
  const bars: StockBar[] = [...stock.data.content]
    .sort((a, b) => b.onHand - a.onHand)
    .slice(0, CHART_PRODUCTS)
    .map((item) => ({ name: item.name, sku: item.sku, available: item.available, reserved: item.reserved }))
  if (bars.length === 0) {
    return <EmptyState title="No products yet"><Link className="text-primary" to="/products">Add a product</Link> to start tracking stock.</EmptyState>
  }
  return <StockChart data={bars} />
}

function PendingQueue() {
  const pending = useQuery({
    queryKey: ['dashboard', 'pending'],
    queryFn: () => unwrap(api.GET('/api/v1/orders', {
      params: { query: { status: 'PENDING', sort: ['createdAt,asc'], page: 0, size: 6 } },
    })),
  })
  if (pending.isPending) return <InlineSpinner label="Loading queue" />
  if (pending.isError) return <ErrorState error={pending.error} onRetry={() => pending.refetch()} />
  if (pending.data.content.length === 0) return <p className="text-sm text-muted-foreground">Nothing waiting. Every order is handled.</p>
  return (
    <ul className="divide-y divide-border">
      {pending.data.content.map((order) => (
        <li key={order.id}>
          <Link to={`/orders/${order.id}`} className="flex items-center justify-between gap-3 py-2.5 hover:text-primary">
            <span>
              <span className="font-heading text-base">{orderReference(order.id)}</span>
              <span className="block text-xs text-muted-foreground">{order.customerName}, {formatDateTime(order.createdAt)}</span>
            </span>
            <span className="tabular-nums">{formatMoney(order.totalAmount)}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

function LowStock() {
  const low = useQuery({
    queryKey: ['dashboard', 'low-stock'],
    queryFn: () => unwrap(api.GET('/api/v1/inventory/low-stock', {
      params: { query: { threshold: LOW_STOCK_THRESHOLD, page: 0, size: 6 } },
    })),
  })
  if (low.isPending) return <InlineSpinner label="Loading" />
  if (low.isError) return <ErrorState error={low.error} onRetry={() => low.refetch()} />
  if (low.data.content.length === 0) return <p className="text-sm text-muted-foreground">Every active product has more than {LOW_STOCK_THRESHOLD} available.</p>
  return (
    <ul className="divide-y divide-border">
      {low.data.content.map((item) => (
        <li key={item.productId} className="flex items-center justify-between gap-3 py-2.5">
          <span className="min-w-0">
            <span className="block truncate">{item.name}</span>
            <span className="block text-xs text-muted-foreground">{item.sku}</span>
          </span>
          <span className={cn('font-heading text-lg tabular-nums', item.available === 0 ? 'text-status-cancelled' : 'text-status-pending')}>
            {formatCount(item.available)}
          </span>
        </li>
      ))}
    </ul>
  )
}

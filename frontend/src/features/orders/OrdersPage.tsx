import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'

import { api, unwrap } from '@/api/client'
import { isStaff, useCurrentUser } from '@/auth/useAuth'
import { PageHeader } from '@/components/layout/PageHeader'
import { OrderStatusBadge, type OrderStatus } from '@/components/orders/OrderStatusBadge'
import { Pagination } from '@/components/Pagination'
import { EmptyState, ErrorState, InlineSpinner } from '@/components/states'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDateTime, formatMoney, orderReference } from '@/lib/format'
import { useDebouncedValue } from '@/lib/useDebouncedValue'

const PAGE_SIZE = 20
type StatusFilter = OrderStatus | 'ALL'
const FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'To pack' },
  { value: 'PACKED', label: 'To ship' },
  { value: 'SHIPPED', label: 'In transit' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

/** Staff see every order and can search by customer; customers see only their own (enforced by the API). */
export function OrdersPage() {
  const user = useCurrentUser()
  const staff = isStaff(user) || user?.role === 'WAREHOUSE'
  const warehouse = user?.role === 'WAREHOUSE'
  const [status, setStatus] = useState<StatusFilter>(warehouse ? 'CONFIRMED' : 'ALL')
  const [emailInput, setEmailInput] = useState('')
  const [page, setPage] = useState(0)
  const email = useDebouncedValue(emailInput.trim(), 300)

  const orders = useQuery({
    queryKey: ['orders', 'list', { status, email, page, warehouse }],
    queryFn: () => unwrap(api.GET('/api/v1/orders', {
      params: {
        query: {
          status: status === 'ALL' ? undefined : status,
          customerEmail: staff && email !== '' ? email : undefined,
          // The warehouse works a queue: oldest first, so nothing waits forever at the bottom.
          sort: warehouse ? ['createdAt,asc'] : undefined,
          page,
          size: PAGE_SIZE,
        },
      },
    })),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader
        title={warehouse ? 'Fulfilment' : staff ? 'Orders' : 'My orders'}
        description={warehouse
          ? 'Orders to pack, ship and deliver. Open one to move it to the next step.'
          : staff ? 'Every order, newest first. Open one to confirm, fulfil or cancel it.'
          : 'Track your orders and cancel them while that is still possible.'}
      />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Tabs value={status} onValueChange={(value) => { setStatus(value as StatusFilter); setPage(0) }}>
          <TabsList className="flex-wrap">
            {FILTERS.map((filter) => <TabsTrigger key={filter.value} value={filter.value}>{filter.label}</TabsTrigger>)}
          </TabsList>
        </Tabs>
        {staff && (
          <div className="relative min-w-60 flex-1 sm:max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              aria-label="Filter by customer email"
              placeholder="Customer email"
              className="pl-9"
              value={emailInput}
              onChange={(event) => { setEmailInput(event.target.value); setPage(0) }}
            />
          </div>
        )}
      </div>

      {orders.isPending ? <InlineSpinner label="Loading orders" />
        : orders.isError ? <ErrorState error={orders.error} onRetry={() => orders.refetch()} />
        : orders.data.content.length === 0 ? (
          <EmptyState title="No orders here">
            {staff ? 'Try another status or clear the email filter.' : <><Link className="text-primary underline-offset-4 hover:underline" to="/shop">Visit the shop</Link> to place your first order.</>}
          </EmptyState>
        ) : (
          <>
            <div className="rounded-xl border border-border bg-panel">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Order</TableHead>
                    {staff && <TableHead>Customer</TableHead>}
                    <TableHead>Status</TableHead>
                    <TableHead>Placed</TableHead>
                    <TableHead className="text-right">Total</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {orders.data.content.map((order) => (
                    <TableRow key={order.id}>
                      <TableCell>
                        <Link className="font-heading text-base text-primary underline-offset-4 hover:underline" to={`/orders/${order.id}`}>
                          {orderReference(order.id)}
                        </Link>
                      </TableCell>
                      {staff && (
                        <TableCell>
                          <p>{order.customerName}</p>
                          <p className="text-xs text-muted-foreground">{order.customerEmail}</p>
                        </TableCell>
                      )}
                      <TableCell><OrderStatusBadge status={order.status} /></TableCell>
                      <TableCell className="text-muted-foreground">{formatDateTime(order.createdAt)}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatMoney(order.totalAmount)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <Pagination page={orders.data.page} totalPages={orders.data.totalPages} totalElements={orders.data.totalElements} onPageChange={setPage} />
          </>
        )}
    </>
  )
}

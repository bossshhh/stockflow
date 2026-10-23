import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'

import { api, unwrap } from '@/api/client'
import type { components } from '@/api/schema'
import { errorMessage } from '@/api/errors'
import { canFulfil, isStaff, useCurrentUser } from '@/auth/useAuth'
import { TrackingLabel } from '@/components/orders/TrackingLabel'
import { canCancel, cancelWindowRemaining, nextFulfilmentAction, type FulfilmentAction } from '@/components/orders/tracking'
import { EmptyState, ErrorState, InlineSpinner } from '@/components/states'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatCountdown, formatMoney, orderReference } from '@/lib/format'
import { useNow } from '@/lib/useNow'

type Order = components['schemas']['OrderResponse']

export function OrderDetailPage() {
  const orderId = parseOrderId(useParams().orderId)
  const order = useQuery({
    queryKey: ['orders', 'detail', orderId],
    queryFn: () => unwrap(api.GET('/api/v1/orders/{id}', { params: { path: { id: orderId ?? 0 } } })),
    enabled: orderId !== null,
  })

  if (orderId === null) {
    return <EmptyState title="That is not an order number">Open an order from your order list.</EmptyState>
  }
  return (
    // minmax(0, 1fr): a plain grid column can't shrink below its widest child (the items table),
    // which pushed the whole page wider than a phone screen.
    <div className="grid grid-cols-[minmax(0,1fr)] gap-6">
      <Link to="/orders" className="inline-flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> All orders
      </Link>
      {order.isPending ? <InlineSpinner label="Loading order" />
        : order.isError ? <ErrorState error={order.error} onRetry={() => order.refetch()} />
        : <OrderView order={order.data} />}
    </div>
  )
}

function OrderView({ order }: { order: Order }) {
  const user = useCurrentUser()
  const staff = isStaff(user)
  // The warehouse fulfils but never cancels (backend rule); customers and staff may cancel.
  const mayCancel = user !== null && user.role !== 'WAREHOUSE'
  const nextStep = canFulfil(user) ? nextFulfilmentAction(order.status) : null
  const now = useNow(1000, order.status === 'CONFIRMED')
  const remaining = cancelWindowRemaining(order, now)

  return (
    <>
      <TrackingLabel order={order} />

      <div className="flex flex-wrap items-center gap-3">
        {staff && order.status === 'PENDING' && <ConfirmButton order={order} />}
        {nextStep !== null && <FulfilmentButton order={order} action={nextStep} />}
        {mayCancel && canCancel(order, now) && <CancelButton order={order} />}
        {order.status === 'CONFIRMED' && (
          <p className="text-sm text-muted-foreground" aria-live="polite">
            {remaining > 0
              ? <>Can still be cancelled for <span className="tabular-nums text-foreground">{formatCountdown(remaining)}</span>, unless it is packed first</>
              : 'The 5-minute cancellation window has closed.'}
          </p>
        )}
      </div>

      <section aria-label="Items" className="rounded-xl border border-border bg-panel">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Product</TableHead>
              <TableHead className="text-right">Unit price</TableHead>
              <TableHead className="text-right">Quantity</TableHead>
              <TableHead className="text-right">Line total</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {order.items.map((item) => (
              <TableRow key={item.productId}>
                <TableCell>
                  <p>{item.productName}</p>
                  <p className="text-xs text-muted-foreground">{item.sku}</p>
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatMoney(item.unitPrice)}</TableCell>
                <TableCell className="text-right tabular-nums">{item.quantity}</TableCell>
                <TableCell className="text-right tabular-nums">{formatMoney(item.lineTotal)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
          <TableFooter>
            <TableRow>
              <TableCell colSpan={3}>Total</TableCell>
              <TableCell className="text-right font-heading text-lg tabular-nums">{formatMoney(order.totalAmount)}</TableCell>
            </TableRow>
          </TableFooter>
        </Table>
        <p className="border-t border-border px-4 py-3 text-xs text-muted-foreground">
          Prices are the ones in effect when the order was placed.
        </p>
      </section>
    </>
  )
}

type OrderAction = 'confirm' | 'cancel' | FulfilmentAction

const ACTION_DONE: Record<OrderAction, string> = {
  confirm: 'confirmed',
  cancel: 'cancelled',
  pack: 'packed',
  ship: 'shipped',
  deliver: 'marked as delivered',
}

function callAction(action: OrderAction, id: number) {
  const params = { params: { path: { id } } }
  switch (action) {
    case 'confirm': return api.POST('/api/v1/orders/{id}/confirm', params)
    case 'cancel': return api.POST('/api/v1/orders/{id}/cancel', params)
    case 'pack': return api.POST('/api/v1/orders/{id}/pack', params)
    case 'ship': return api.POST('/api/v1/orders/{id}/ship', params)
    case 'deliver': return api.POST('/api/v1/orders/{id}/deliver', params)
  }
}

function useOrderAction(order: Order, action: OrderAction) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(callAction(action, order.id)),
    onSuccess: (updated) => {
      queryClient.setQueryData(['orders', 'detail', order.id], updated)
      void queryClient.invalidateQueries({ queryKey: ['orders', 'list'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      toast.success(`${orderReference(order.id)} ${ACTION_DONE[action]}`)
    },
    onError: (error) => toast.error(errorMessage(error)),
  })
}

const FULFILMENT_LABEL: Record<FulfilmentAction, { idle: string; busy: string }> = {
  pack: { idle: 'Mark as packed', busy: 'Saving…' },
  ship: { idle: 'Mark as shipped', busy: 'Saving…' },
  deliver: { idle: 'Mark as delivered', busy: 'Saving…' },
}

function FulfilmentButton({ order, action }: { order: Order; action: FulfilmentAction }) {
  const mutation = useOrderAction(order, action)
  const label = FULFILMENT_LABEL[action]
  return (
    <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
      {mutation.isPending ? label.busy : label.idle}
    </Button>
  )
}

function ConfirmButton({ order }: { order: Order }) {
  const confirm = useOrderAction(order, 'confirm')
  return (
    <Button onClick={() => confirm.mutate()} disabled={confirm.isPending}>
      {confirm.isPending ? 'Confirming…' : 'Confirm order'}
    </Button>
  )
}

function CancelButton({ order }: { order: Order }) {
  const cancel = useOrderAction(order, 'cancel')
  const effect = order.status === 'PENDING' ? 'The reserved stock is released.' : 'The items go back into stock.'
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant="destructive" disabled={cancel.isPending}>{cancel.isPending ? 'Cancelling…' : 'Cancel order'}</Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Cancel {orderReference(order.id)}?</AlertDialogTitle>
          <AlertDialogDescription>{effect} A cancelled order cannot be reopened.</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Keep order</AlertDialogCancel>
          <AlertDialogAction onClick={() => cancel.mutate()}>Cancel order</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

function parseOrderId(raw: string | undefined): number | null {
  if (raw === undefined || !/^\d{1,18}$/.test(raw)) return null
  const id = Number(raw)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

import { cn } from '@/lib/utils'
import type { components } from '@/api/schema'

export type OrderStatus = components['schemas']['OrderResponse']['status']

const WAITING = 'border-status-pending/40 bg-status-pending/10 text-status-pending'
const IN_PROGRESS = 'border-status-info/40 bg-status-info/10 text-status-info'
const DONE = 'border-status-confirmed/40 bg-status-confirmed/10 text-status-confirmed'
const STOPPED = 'border-status-cancelled/40 bg-status-cancelled/10 text-status-cancelled'

/** Amber = waiting on us, blue = moving through the warehouse, teal = delivered, red = cancelled. */
const STATUS_STYLE: Record<OrderStatus, { label: string; className: string }> = {
  PENDING: { label: 'Pending', className: WAITING },
  CONFIRMED: { label: 'Confirmed', className: IN_PROGRESS },
  PACKED: { label: 'Packed', className: IN_PROGRESS },
  SHIPPED: { label: 'Shipped', className: IN_PROGRESS },
  DELIVERED: { label: 'Delivered', className: DONE },
  CANCELLED: { label: 'Cancelled', className: STOPPED },
}

export function OrderStatusBadge({ status, className }: { status: OrderStatus; className?: string }) {
  const style = STATUS_STYLE[status]
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium', style.className, className)}>
      <span className="size-1.5 rounded-full bg-current" aria-hidden />
      {style.label}
    </span>
  )
}

export function StockPill({ inStock }: { inStock: boolean }) {
  return (
    <span className={cn(
      'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium',
      inStock ? 'border-status-confirmed/40 text-status-confirmed' : 'border-border text-muted-foreground',
    )}>
      <span className="size-1.5 rounded-full bg-current" aria-hidden />
      {inStock ? 'In stock' : 'Out of stock'}
    </span>
  )
}

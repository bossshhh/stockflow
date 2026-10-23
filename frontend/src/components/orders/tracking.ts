import type { components } from '@/api/schema'

type OrderStatus = components['schemas']['OrderResponse']['status']
type HistoryEntry = components['schemas']['OrderStatusEventResponse']
type Order = Pick<components['schemas']['OrderResponse'], 'status' | 'confirmedAt'> & {
  history: readonly HistoryEntry[]
}

export type StepState = 'done' | 'current' | 'upcoming' | 'cancelled'

export interface TrackingStep {
  key: Exclude<OrderStatus, 'CANCELLED'> | 'CANCELLED'
  label: string
  at: string | null
  state: StepState
  note: string
}

/** The route every order follows when nothing goes wrong. */
const ROUTE: readonly { status: Exclude<OrderStatus, 'CANCELLED'>; label: string; done: string; waiting: string }[] = [
  { status: 'PENDING', label: 'Placed', done: 'Stock reserved for this order', waiting: '' },
  { status: 'CONFIRMED', label: 'Confirmed', done: 'Taken from stock', waiting: 'Waiting for the warehouse to confirm' },
  { status: 'PACKED', label: 'Packed', done: 'Packed and ready to go', waiting: 'Not packed yet' },
  { status: 'SHIPPED', label: 'Shipped', done: 'On its way', waiting: 'Not shipped yet' },
  { status: 'DELIVERED', label: 'Delivered', done: 'Delivered', waiting: 'Not delivered yet' },
]

/**
 * The tracking route, built from the order's recorded status history (real timestamps, never invented).
 * A cancelled order shows the checkpoints it actually reached, then the cancellation.
 */
export function buildTrackingSteps(order: Order): TrackingStep[] {
  const reachedAt = new Map<OrderStatus, string>()
  for (const entry of order.history) {
    if (!reachedAt.has(entry.status)) reachedAt.set(entry.status, entry.at)
  }

  if (order.status === 'CANCELLED') {
    const reached = ROUTE.filter((stage) => reachedAt.has(stage.status))
    const restocked = reachedAt.has('CONFIRMED')
    return [
      ...reached.map((stage) => step(stage, reachedAt.get(stage.status) ?? null, 'done')),
      {
        key: 'CANCELLED',
        label: 'Cancelled',
        at: reachedAt.get('CANCELLED') ?? null,
        state: 'cancelled',
        note: restocked ? 'Items returned to stock' : 'Reservation released',
      },
    ]
  }

  const currentIndex = ROUTE.findIndex((stage) => stage.status === order.status)
  return ROUTE.map((stage, index) => {
    if (index < currentIndex) return step(stage, reachedAt.get(stage.status) ?? null, 'done')
    if (index === currentIndex) return step(stage, reachedAt.get(stage.status) ?? null, order.status === 'DELIVERED' ? 'done' : 'current')
    return step(stage, null, 'upcoming')
  })
}

function step(stage: (typeof ROUTE)[number], at: string | null, state: StepState): TrackingStep {
  return {
    key: stage.status,
    label: stage.label,
    at,
    state,
    note: state === 'upcoming' ? stage.waiting : stage.done,
  }
}

/** Mirrors the backend default (stockflow.orders.confirmed-cancel-window). The backend enforces it. */
export const CONFIRMED_CANCEL_WINDOW_MS = 5 * 60 * 1000

/** Milliseconds left to cancel a confirmed order; 0 when the window has closed or does not apply. */
export function cancelWindowRemaining(order: Pick<Order, 'status' | 'confirmedAt'>, nowMs: number): number {
  if (order.status !== 'CONFIRMED' || order.confirmedAt === null || order.confirmedAt === undefined) return 0
  const confirmedMs = Date.parse(order.confirmedAt)
  if (Number.isNaN(confirmedMs)) return 0
  // Clamped to the window: the browser's clock can run slightly behind the server's, which would
  // otherwise show "5:01" for a 5-minute window. The backend decides; this is display only.
  return Math.min(CONFIRMED_CANCEL_WINDOW_MS, Math.max(0, confirmedMs + CONFIRMED_CANCEL_WINDOW_MS - nowMs))
}

/** Pending orders can always be cancelled; confirmed ones within the window; packed or later never. */
export function canCancel(order: Pick<Order, 'status' | 'confirmedAt'>, nowMs: number): boolean {
  return order.status === 'PENDING' || cancelWindowRemaining(order, nowMs) > 0
}

export type FulfilmentAction = 'pack' | 'ship' | 'deliver'

/** The single next warehouse step for an order, if any. */
export function nextFulfilmentAction(status: OrderStatus): FulfilmentAction | null {
  switch (status) {
    case 'CONFIRMED': return 'pack'
    case 'PACKED': return 'ship'
    case 'SHIPPED': return 'deliver'
    default: return null
  }
}

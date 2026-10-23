import { Check, X } from 'lucide-react'

import { formatDateTime, orderReference } from '@/lib/format'
import { cn } from '@/lib/utils'
import { OrderStatusBadge, type OrderStatus } from './OrderStatusBadge'
import { buildTrackingSteps, type StepState, type TrackingStep } from './tracking'

interface TrackingLabelProps {
  order: {
    id: number
    status: OrderStatus
    confirmedAt?: string | null
    customerName: string
    history: readonly { status: OrderStatus; at: string }[]
  }
}

/**
 * Order tracking drawn like a shipping label: the reference and a barcode on top, the route of
 * checkpoints below. The one deliberately bold element of the interface.
 */
export function TrackingLabel({ order }: TrackingLabelProps) {
  const steps = buildTrackingSteps(order)
  const reference = orderReference(order.id)

  return (
    <section aria-label={`Tracking for order ${reference}`} className="overflow-hidden rounded-xl border-2 border-dashed border-border bg-panel">
      <header className="flex flex-wrap items-end justify-between gap-4 border-b-2 border-dashed border-border px-5 py-4">
        <div>
          <p className="text-sm text-muted-foreground">Order for {order.customerName}</p>
          <p className="font-heading text-4xl font-semibold tracking-tight">{reference}</p>
        </div>
        <div className="flex flex-col items-end gap-2">
          <Barcode value={reference} />
          <OrderStatusBadge status={order.status} />
        </div>
      </header>

      <ol className="grid gap-0 px-5 py-6 md:grid-flow-col md:auto-cols-fr">
        {steps.map((step, index) => (
          <Checkpoint key={step.key} step={step} isLast={index === steps.length - 1} />
        ))}
      </ol>
    </section>
  )
}

const MARKER: Record<StepState, string> = {
  done: 'border-status-confirmed bg-status-confirmed text-asphalt',
  current: 'border-primary bg-primary text-primary-foreground ring-4 ring-primary/20',
  upcoming: 'border-dashed border-muted-foreground/60 bg-transparent text-muted-foreground',
  cancelled: 'border-status-cancelled bg-status-cancelled text-asphalt',
}

function Checkpoint({ step, isLast }: { step: TrackingStep; isLast: boolean }) {
  const reached = step.state !== 'upcoming'
  return (
    <li className="relative flex gap-4 pb-6 md:flex-col md:gap-3 md:pb-0" aria-current={step.state === 'current' ? 'step' : undefined}>
      {!isLast && (
        <span
          aria-hidden
          className={cn(
            'absolute left-[15px] top-8 h-[calc(100%-2rem)] w-0.5 md:left-8 md:top-[15px] md:h-0.5 md:w-[calc(100%-2rem)]',
            reached ? 'bg-status-confirmed/60' : 'border-l-2 border-dashed border-border md:border-l-0 md:border-t-2',
          )}
        />
      )}
      <span className={cn('relative z-10 grid size-8 shrink-0 place-items-center rounded-full border-2', MARKER[step.state])}>
        {step.state === 'cancelled' ? <X className="size-4" /> : reached ? <Check className="size-4" /> : null}
      </span>
      <div className="min-w-0">
        <p className={cn('font-heading text-lg leading-tight', !reached && 'text-muted-foreground')}>{step.label}</p>
        <p className="text-sm text-muted-foreground">{step.at === null ? 'Not yet' : formatDateTime(step.at)}</p>
        <p className="mt-1 text-sm">{step.note}</p>
      </div>
    </li>
  )
}

/** Decorative barcode derived from the reference (not machine-readable; hidden from assistive tech). */
function Barcode({ value }: { value: string }) {
  const widths = Array.from(value).flatMap((char) => {
    const code = char.charCodeAt(0)
    return [1 + (code % 3), 1 + ((code >> 2) % 2), 1 + ((code >> 3) % 3)]
  })
  return (
    <div aria-hidden className="flex h-9 items-stretch gap-px">
      {widths.map((width, index) => (
        <span
          key={index}
          className={index % 2 === 0 ? 'bg-foreground/85' : 'bg-transparent'}
          style={{ width: `${width * 2}px` }}
        />
      ))}
    </div>
  )
}

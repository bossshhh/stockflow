import { AlertTriangle, Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'

import { Button } from '@/components/ui/button'
import { errorMessage } from '@/api/errors'

export function FullPageSpinner() {
  return (
    <div className="grid min-h-dvh place-items-center" role="status" aria-label="Loading">
      <Loader2 className="size-6 animate-spin text-muted-foreground" />
    </div>
  )
}

export function InlineSpinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 py-10 text-sm text-muted-foreground" role="status">
      <Loader2 className="size-4 animate-spin" /> {label}
    </div>
  )
}

/** Errors explain what happened and offer the way forward. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-lg border border-status-cancelled/40 bg-status-cancelled/5 p-4">
      <p className="flex items-center gap-2 text-sm text-foreground">
        <AlertTriangle className="size-4 text-status-cancelled" aria-hidden />
        {errorMessage(error)}
      </p>
      {onRetry !== undefined && (
        <Button size="sm" variant="outline" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

/** An empty screen is an invitation to act. */
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-start gap-2 rounded-lg border border-dashed border-border px-5 py-8">
      <p className="font-heading text-lg">{title}</p>
      {children !== undefined && <div className="text-sm text-muted-foreground">{children}</div>}
    </div>
  )
}

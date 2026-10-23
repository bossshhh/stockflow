import type { ReactNode } from 'react'

import { Label } from '@/components/ui/label'

/** Label + control + error message, wired for screen readers. */
export function Field({ id, label, error, hint, children }: {
  id: string
  label: string
  error?: string
  hint?: string
  children: ReactNode
}) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error !== undefined ? (
        <p id={`${id}-error`} className="text-sm text-status-cancelled" role="alert">{error}</p>
      ) : hint !== undefined ? (
        <p id={`${id}-hint`} className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  )
}

import type { ReactNode } from 'react'

/**
 * Page title with the amber rule from the reference design; actions sit on the right. The rule is anchored
 * to the header's bottom border (not to the heading), so it sits on the divider however tall the
 * description is.
 */
export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="relative mb-6 flex flex-wrap items-end justify-between gap-4 border-b border-border pb-4">
      <div>
        <h1 className="font-heading text-3xl font-semibold">{title}</h1>
        {description !== undefined && <p className="mt-1 max-w-prose text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions !== undefined && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      <span aria-hidden className="absolute -bottom-px left-0 h-0.5 w-16 bg-primary" />
    </div>
  )
}

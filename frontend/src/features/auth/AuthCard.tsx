import type { ReactNode } from 'react'

export function AuthCard({ title, footer, children }: { title: string; footer: ReactNode; children: ReactNode }) {
  return (
    <div className="mx-auto mt-6 w-full max-w-sm md:mt-16">
      <div className="rounded-xl border border-border bg-panel p-6">
        <h1 className="mb-6 font-heading text-3xl font-semibold">{title}</h1>
        {children}
      </div>
      <p className="mt-4 text-center text-sm text-muted-foreground">{footer}</p>
    </div>
  )
}

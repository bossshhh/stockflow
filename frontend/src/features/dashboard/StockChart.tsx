import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { formatCount } from '@/lib/format'

export interface StockBar {
  name: string
  sku: string
  available: number
  reserved: number
}

const AVAILABLE = 'var(--chart-available)'
const RESERVED = 'var(--chart-reserved)'
const SURFACE = 'var(--panel)'

/**
 * On-hand stock per product, split into available and reserved (part-to-whole), as horizontal stacked
 * bars. Two series: a legend is always shown, and the tooltip names both values. Color is never the
 * only cue: the legend and tooltip carry the words.
 */
export function StockChart({ data }: { data: StockBar[] }) {
  const height = Math.max(160, data.length * 34 + 40)
  return (
    <figure className="m-0">
      <Legend />
      <div style={{ height }} role="img" aria-label={describe(data)}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, bottom: 4, left: 8 }} barCategoryGap={8}>
            <CartesianGrid horizontal={false} stroke="var(--line)" strokeDasharray="2 4" />
            <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: 'var(--paper-muted)', fontSize: 12 }} tickFormatter={formatCount} />
            <YAxis type="category" dataKey="name" width={140} tickLine={false} axisLine={false} tick={{ fill: 'var(--paper)', fontSize: 13 }} />
            <Tooltip cursor={{ fill: 'var(--panel-raised)' }} content={StockTooltip} />
            {/* 2px surface-colored stroke = the gap between stacked segments. */}
            <Bar dataKey="available" stackId="stock" fill={AVAILABLE} stroke={SURFACE} strokeWidth={2} isAnimationActive={false} />
            <Bar dataKey="reserved" stackId="stock" fill={RESERVED} stroke={SURFACE} strokeWidth={2} radius={[0, 4, 4, 0]} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </figure>
  )
}

function Legend() {
  return (
    <div className="mb-3 flex gap-4 text-sm text-muted-foreground">
      <span className="flex items-center gap-2"><span className="size-3 rounded-sm" style={{ background: AVAILABLE }} aria-hidden />Available</span>
      <span className="flex items-center gap-2"><span className="size-3 rounded-sm" style={{ background: RESERVED }} aria-hidden />Reserved for pending orders</span>
    </div>
  )
}

/** Only the props this tooltip reads; recharts passes more. */
interface StockTooltipProps {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: unknown }>
}

function StockTooltip({ active, payload }: StockTooltipProps) {
  const bar = payload?.[0]?.payload as StockBar | undefined
  if (active !== true || bar === undefined) return null
  return (
    <div className="rounded-lg border border-border bg-popover px-3 py-2 text-sm shadow-none">
      <p className="font-medium text-foreground">{bar.name}</p>
      <p className="mb-1 text-xs text-muted-foreground">{bar.sku}</p>
      <p className="text-foreground"><span className="text-muted-foreground">Available</span> {formatCount(bar.available)}</p>
      <p className="text-foreground"><span className="text-muted-foreground">Reserved</span> {formatCount(bar.reserved)}</p>
      <p className="text-foreground"><span className="text-muted-foreground">On hand</span> {formatCount(bar.available + bar.reserved)}</p>
    </div>
  )
}

function describe(data: StockBar[]): string {
  if (data.length === 0) return 'No stock to show'
  return `Stock for ${data.length} products. ` + data
    .map((bar) => `${bar.name}: ${bar.available} available, ${bar.reserved} reserved`)
    .join('; ')
}

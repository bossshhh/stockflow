const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const compactMoney = new Intl.NumberFormat('en-US', {
  style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1,
})
const integer = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 })
const dateTime = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
})
const dateOnly = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })

/** Prices arrive as JSON numbers with 2 decimals (the backend uses BigDecimal/NUMERIC). */
export function formatMoney(amount: number): string {
  if (!Number.isFinite(amount)) return '—'
  return money.format(amount)
}

/** Short form for headline figures ($1.2M) once full amounts stop fitting; exact below ten million. */
export function formatMoneyShort(amount: number): string {
  if (!Number.isFinite(amount)) return '—'
  return Math.abs(amount) >= 10_000_000 ? compactMoney.format(amount) : money.format(amount)
}

export function formatCount(value: number): string {
  if (!Number.isFinite(value)) return '—'
  return integer.format(value)
}

export function formatDateTime(iso: string | null | undefined): string {
  const date = parseIso(iso)
  return date === null ? '—' : dateTime.format(date)
}

export function formatDate(iso: string | null | undefined): string {
  const date = parseIso(iso)
  return date === null ? '—' : dateOnly.format(date)
}

/** Order ids shown like a shipping reference: 42 -> "SF-000042". */
export function orderReference(id: number): string {
  if (!Number.isInteger(id) || id < 0) return 'SF-?'
  return `SF-${String(id).padStart(6, '0')}`
}

/** mm:ss for countdowns; never negative. */
export function formatCountdown(milliseconds: number): string {
  const totalSeconds = Math.max(0, Math.ceil(milliseconds / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

function parseIso(iso: string | null | undefined): Date | null {
  if (iso === null || iso === undefined || iso === '') return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

import { z } from 'zod'

/**
 * The Idempotency-Key sent with "Place order". One key per exact cart:
 * - the same cart submitted again (double click, retry after a lost response, even after a reload) sends
 *   the same key, so the server returns the order it already placed instead of placing a second one;
 * - a changed cart gets a new key (the server would refuse the old key with different items);
 * - after a successful order the key is dropped, so the next order is a new one.
 * Kept in sessionStorage: it survives a reload of this tab, and other tabs have their own.
 */
const STORAGE_KEY = 'stockflow.checkout-key.v1'
const storedSchema = z.object({ signature: z.string(), key: z.string().regex(/^[A-Za-z0-9_-]{8,64}$/) })
type Stored = z.infer<typeof storedSchema>

interface CheckoutItem {
  productId: number
  quantity: number
}

let current: Stored | null = load()

function load(): Stored | null {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY)
    if (raw === null) return null
    const parsed = storedSchema.safeParse(JSON.parse(raw))
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}

function save(value: Stored | null): void {
  current = value
  try {
    if (value === null) window.sessionStorage.removeItem(STORAGE_KEY)
    else window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value))
  } catch {
    // Storage blocked: the key still protects against double clicks within this page view.
  }
}

/** Same idea as the server's request hash: merged quantities, sorted by product id. */
function signature(items: readonly CheckoutItem[]): string {
  const merged = new Map<number, number>()
  for (const item of items) merged.set(item.productId, (merged.get(item.productId) ?? 0) + item.quantity)
  return [...merged].sort(([a], [b]) => a - b).map(([id, quantity]) => `${id}:${quantity}`).join(',')
}

export const checkoutKey = {
  keyFor(items: readonly CheckoutItem[]): string {
    const wanted = signature(items)
    if (current?.signature === wanted) return current.key
    const next = { signature: wanted, key: crypto.randomUUID() }
    save(next)
    return next.key
  },
  reset(): void {
    save(null)
  },
}

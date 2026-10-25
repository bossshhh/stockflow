import { z } from 'zod'

import type { components } from '@/api/schema'

/**
 * The Idempotency-Key sent with "Place order". One key per exact cart:
 * - the same cart submitted again (double click, retry after a lost response, even after a reload) sends
 *   the same key, so the server returns the order it already placed instead of placing a second one;
 * - a changed cart or address gets a new key (the server would refuse the old key with a different request);
 * - after a successful order the key is dropped, so the next order is a new one.
 * Kept in sessionStorage: it survives a reload of this tab, and other tabs have their own. Only a hash of the
 * cart and address is stored, never the address itself: on a shared computer the next person using this tab
 * must not be able to read it.
 */
const STORAGE_KEY = 'stockflow.checkout-key.v1'
const storedSchema = z.object({ signature: z.string(), key: z.string().regex(/^[A-Za-z0-9_-]{8,64}$/) })
type Stored = z.infer<typeof storedSchema>

interface CheckoutItem {
  productId: number
  quantity: number
}

type ShippingAddress = components['schemas']['ShippingAddressRequest']

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

/**
 * Same idea as the server's request hash: merged quantities sorted by product id, then the trimmed address
 * fields (the server trims them too, so " Berlin " and "Berlin" are the same request).
 */
function signature(items: readonly CheckoutItem[], address: ShippingAddress): string {
  const merged = new Map<number, number>()
  for (const item of items) merged.set(item.productId, (merged.get(item.productId) ?? 0) + item.quantity)
  const itemsPart = [...merged].sort(([a], [b]) => a - b).map(([id, quantity]) => `${id}:${quantity}`).join(',')
  const { name, street, houseNumber, postcode, city } = address
  return hash([itemsPart, name, street, houseNumber, postcode, city].map((part) => part.trim()).join('\n'))
}

/**
 * cyrb53: a fast, synchronous 53-bit string hash (crypto.subtle is async, and this only needs equality).
 * A collision would reuse a key for a different request; the server then answers 422 and the customer
 * retries, so a 1-in-2^53 chance is acceptable.
 */
function hash(text: string): string {
  let h1 = 0xdeadbeef
  let h2 = 0x41c6ce57
  for (let i = 0; i < text.length; i++) {
    const ch = text.charCodeAt(i)
    h1 = Math.imul(h1 ^ ch, 2654435761)
    h2 = Math.imul(h2 ^ ch, 1597334677)
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909)
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909)
  return (4294967296 * (2097151 & h2) + (h1 >>> 0)).toString(36)
}

export const checkoutKey = {
  keyFor(items: readonly CheckoutItem[], address: ShippingAddress): string {
    const wanted = signature(items, address)
    if (current?.signature === wanted) return current.key
    const next = { signature: wanted, key: crypto.randomUUID() }
    save(next)
    return next.key
  },
  reset(): void {
    save(null)
  },
}

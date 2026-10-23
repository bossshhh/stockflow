import { useSyncExternalStore } from 'react'
import { z } from 'zod'

/**
 * The shopping cart, kept per browser in localStorage. It is a convenience only: prices and stock are
 * re-checked by the backend when the order is placed, so a stale cart can never cause a wrong order.
 */
export const MAX_QUANTITY_PER_PRODUCT = 10_000
export const MAX_CART_LINES = 50
const STORAGE_KEY = 'stockflow.cart.v1'

const cartLineSchema = z.object({
  productId: z.number().int().positive(),
  sku: z.string().min(1),
  name: z.string().min(1),
  price: z.number().nonnegative(),
  quantity: z.number().int().min(1).max(MAX_QUANTITY_PER_PRODUCT),
})
const cartSchema = z.array(cartLineSchema).max(MAX_CART_LINES)

export type CartLine = z.infer<typeof cartLineSchema>

let lines: CartLine[] = load()
const listeners = new Set<() => void>()

function load(): CartLine[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw === null) return []
    const parsed = cartSchema.safeParse(JSON.parse(raw))
    // Corrupt or outdated data is discarded instead of crashing the app.
    return parsed.success ? parsed.data : []
  } catch {
    return []
  }
}

function commit(next: CartLine[]): void {
  lines = next
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {
    // Storage full or blocked (private mode): the cart still works for this page view.
  }
  listeners.forEach((listener) => listener())
}

export const cart = {
  add(item: Omit<CartLine, 'quantity'>, quantity: number): void {
    if (!Number.isInteger(quantity) || quantity < 1) {
      throw new Error('Quantity must be a positive whole number')
    }
    const existing = lines.find((line) => line.productId === item.productId)
    if (existing === undefined && lines.length >= MAX_CART_LINES) {
      throw new Error(`A cart can hold at most ${MAX_CART_LINES} different products`)
    }
    const next = existing === undefined
      ? [...lines, cartLineSchema.parse({ ...item, quantity: Math.min(quantity, MAX_QUANTITY_PER_PRODUCT) })]
      : lines.map((line) => line.productId === item.productId
          ? { ...line, quantity: Math.min(line.quantity + quantity, MAX_QUANTITY_PER_PRODUCT) }
          : line)
    commit(next)
  },
  setQuantity(productId: number, quantity: number): void {
    if (!Number.isInteger(quantity)) return
    if (quantity < 1) {
      cart.remove(productId)
      return
    }
    commit(lines.map((line) => line.productId === productId
      ? { ...line, quantity: Math.min(quantity, MAX_QUANTITY_PER_PRODUCT) }
      : line))
  },
  remove(productId: number): void {
    commit(lines.filter((line) => line.productId !== productId))
  },
  clear(): void {
    commit([])
  },
  subscribe(listener: () => void): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
  snapshot(): CartLine[] {
    return lines
  },
}

// Keep tabs in sync: another tab changing the cart fires a "storage" event here.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === STORAGE_KEY) {
      lines = load()
      listeners.forEach((listener) => listener())
    }
  })
}

export function useCart(): CartLine[] {
  return useSyncExternalStore(cart.subscribe, cart.snapshot, cart.snapshot)
}

export function cartTotal(items: readonly CartLine[]): number {
  // Cents avoid floating-point drift when adding many prices.
  const cents = items.reduce((sum, line) => sum + Math.round(line.price * 100) * line.quantity, 0)
  return cents / 100
}

export function cartCount(items: readonly CartLine[]): number {
  return items.reduce((sum, line) => sum + line.quantity, 0)
}

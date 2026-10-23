import { beforeEach, describe, expect, it, vi } from 'vitest'

const KEY = 'stockflow.cart.v1'
const apple = { productId: 1, sku: 'APPLE', name: 'Apple', price: 0.1 }

/** Fresh module per test, because the store reads localStorage when it is first imported. */
async function loadStore() {
  vi.resetModules()
  return import('./cart-store')
}

describe('cart store', () => {
  beforeEach(() => window.localStorage.clear())

  it('discards corrupt or tampered storage instead of crashing', async () => {
    window.localStorage.setItem(KEY, JSON.stringify([{ productId: -5, quantity: 'lots' }]))
    const { cart } = await loadStore()
    expect(cart.snapshot()).toEqual([])
  })

  it('merges repeated adds of the same product and caps the quantity', async () => {
    const { cart, MAX_QUANTITY_PER_PRODUCT } = await loadStore()
    cart.add(apple, 2)
    cart.add(apple, 3)
    expect(cart.snapshot()).toEqual([{ ...apple, quantity: 5 }])
    cart.add(apple, MAX_QUANTITY_PER_PRODUCT)
    expect(cart.snapshot()[0]?.quantity).toBe(MAX_QUANTITY_PER_PRODUCT)
  })

  it('rejects non-whole quantities', async () => {
    const { cart } = await loadStore()
    expect(() => cart.add(apple, 1.5)).toThrow()
    expect(() => cart.add(apple, 0)).toThrow()
  })

  it('removes a line when its quantity drops below 1', async () => {
    const { cart } = await loadStore()
    cart.add(apple, 2)
    cart.setQuantity(apple.productId, 0)
    expect(cart.snapshot()).toEqual([])
  })

  it('totals in cents, so 3 x 0.10 is exactly 0.30', async () => {
    const { cartTotal } = await loadStore()
    expect(cartTotal([{ ...apple, quantity: 3 }])).toBe(0.3)
  })

  it('persists across reloads', async () => {
    const first = await loadStore()
    first.cart.add(apple, 4)
    const second = await loadStore()
    expect(second.cart.snapshot()).toEqual([{ ...apple, quantity: 4 }])
  })
})

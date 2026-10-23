import { beforeEach, describe, expect, it, vi } from 'vitest'

import { checkoutKey } from './checkout-key'

const apple = { productId: 1, quantity: 2 }
const pear = { productId: 2, quantity: 1 }

describe('checkout idempotency key', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    checkoutKey.reset()
  })

  it('reuses the key when the same cart is submitted again (retry after a lost response)', () => {
    const first = checkoutKey.keyFor([apple, pear])
    expect(checkoutKey.keyFor([apple, pear])).toBe(first)
  })

  it('treats the same items in another order as the same cart, like the server does', () => {
    expect(checkoutKey.keyFor([pear, apple])).toBe(checkoutKey.keyFor([apple, pear]))
  })

  it('makes a new key when the cart changes', () => {
    const first = checkoutKey.keyFor([apple])
    expect(checkoutKey.keyFor([{ ...apple, quantity: 3 }])).not.toBe(first)
  })

  it('makes a new key after a successful order, so the next order is not a replay', () => {
    const first = checkoutKey.keyFor([apple])
    checkoutKey.reset()
    expect(checkoutKey.keyFor([apple])).not.toBe(first)
  })

  it('survives a page reload in the same tab', async () => {
    const first = checkoutKey.keyFor([apple])
    vi.resetModules()
    const reloaded = await import('./checkout-key')
    expect(reloaded.checkoutKey.keyFor([apple])).toBe(first)
  })

  it('produces keys the server accepts (8-64 of A-Z a-z 0-9 - _)', () => {
    expect(checkoutKey.keyFor([apple])).toMatch(/^[A-Za-z0-9_-]{8,64}$/)
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'

import { checkoutKey } from './checkout-key'

const apple = { productId: 1, quantity: 2 }
const pear = { productId: 2, quantity: 1 }
const berlin = { name: 'Ada Lovelace', street: 'Musterstraße', houseNumber: '12a', postcode: '10115', city: 'Berlin' }

describe('checkout idempotency key', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    checkoutKey.reset()
  })

  it('reuses the key when the same cart is submitted again (retry after a lost response)', () => {
    const first = checkoutKey.keyFor([apple, pear], berlin)
    expect(checkoutKey.keyFor([apple, pear], berlin)).toBe(first)
  })

  it('treats the same items in another order as the same cart, like the server does', () => {
    expect(checkoutKey.keyFor([pear, apple], berlin)).toBe(checkoutKey.keyFor([apple, pear], berlin))
  })

  it('makes a new key when the cart changes', () => {
    const first = checkoutKey.keyFor([apple], berlin)
    expect(checkoutKey.keyFor([{ ...apple, quantity: 3 }], berlin)).not.toBe(first)
  })

  it('makes a new key after a successful order, so the next order is not a replay', () => {
    const first = checkoutKey.keyFor([apple], berlin)
    checkoutKey.reset()
    expect(checkoutKey.keyFor([apple], berlin)).not.toBe(first)
  })

  it('survives a page reload in the same tab', async () => {
    const first = checkoutKey.keyFor([apple], berlin)
    vi.resetModules()
    const reloaded = await import('./checkout-key')
    expect(reloaded.checkoutKey.keyFor([apple], berlin)).toBe(first)
  })

  it('makes a new key when the address changes (the server refuses the old key with another address)', () => {
    const first = checkoutKey.keyFor([apple], berlin)
    expect(checkoutKey.keyFor([apple], { ...berlin, postcode: '20095' })).not.toBe(first)
  })

  it('treats the same address with extra spaces as the same address, like the server does', () => {
    const first = checkoutKey.keyFor([apple], berlin)
    expect(checkoutKey.keyFor([apple], { ...berlin, city: '  Berlin ' })).toBe(first)
  })

  it('never stores the address itself in sessionStorage (shared computers)', () => {
    checkoutKey.keyFor([apple], berlin)
    const stored = Object.values({ ...window.sessionStorage }).join(' ')
    expect(stored).not.toBe('')
    for (const part of ['Ada Lovelace', 'Musterstraße', '10115', 'Berlin']) {
      expect(stored).not.toContain(part)
    }
  })

  it('produces keys the server accepts (8-64 of A-Z a-z 0-9 - _)', () => {
    expect(checkoutKey.keyFor([apple], berlin)).toMatch(/^[A-Za-z0-9_-]{8,64}$/)
  })
})

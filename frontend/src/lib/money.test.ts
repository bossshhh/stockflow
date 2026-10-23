import { describe, expect, it } from 'vitest'

import { formatCountdown, formatMoneyShort, orderReference } from './format'
import { hasAtMostTwoDecimals } from './money'

describe('hasAtMostTwoDecimals', () => {
  it('accepts prices that floating point gets wrong when multiplied (19.9 * 100 = 1989.9999...)', () => {
    expect(hasAtMostTwoDecimals(19.9)).toBe(true)
    expect(hasAtMostTwoDecimals(0.29)).toBe(true)
    expect(hasAtMostTwoDecimals(9999999999.99)).toBe(true)
  })

  it('rejects a third decimal and non-numbers', () => {
    expect(hasAtMostTwoDecimals(1.999)).toBe(false)
    expect(hasAtMostTwoDecimals(Number.NaN)).toBe(false)
    expect(hasAtMostTwoDecimals(Number.POSITIVE_INFINITY)).toBe(false)
  })
})

describe('format helpers', () => {
  it('shows order ids as shipping references', () => {
    expect(orderReference(42)).toBe('SF-000042')
    expect(orderReference(-1)).toBe('SF-?')
  })

  it('abbreviates only amounts that would not fit a headline', () => {
    expect(formatMoneyShort(9_999_999.99)).toBe('$9,999,999.99')
    expect(formatMoneyShort(500_002_146_046_252)).toBe('$500T')
  })

  it('formats countdowns and never goes negative', () => {
    expect(formatCountdown(4 * 60_000 + 5_000)).toBe('4:05')
    expect(formatCountdown(-10)).toBe('0:00')
  })
})

import { describe, expect, it } from 'vitest'

import {
  buildTrackingSteps,
  canCancel,
  cancelWindowRemaining,
  CONFIRMED_CANCEL_WINDOW_MS,
  nextFulfilmentAction,
} from './tracking'

const PLACED = '2026-01-15T10:00:00Z'
const CONFIRMED = '2026-01-15T10:02:00Z'
const PACKED = '2026-01-15T11:00:00Z'
const SHIPPED = '2026-01-15T15:00:00Z'
const DELIVERED = '2026-01-16T09:00:00Z'
const CANCELLED = '2026-01-15T10:03:00Z'

const at = (status: 'PENDING' | 'CONFIRMED' | 'PACKED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED', time: string) =>
  ({ status, at: time })

describe('buildTrackingSteps', () => {
  it('pending: placed is current and the rest of the route is still ahead', () => {
    const steps = buildTrackingSteps({ status: 'PENDING', confirmedAt: null, history: [at('PENDING', PLACED)] })
    expect(steps.map((step) => [step.key, step.state])).toEqual([
      ['PENDING', 'current'], ['CONFIRMED', 'upcoming'], ['PACKED', 'upcoming'], ['SHIPPED', 'upcoming'], ['DELIVERED', 'upcoming'],
    ])
    expect(steps[1]?.note).toBe('Waiting for the warehouse to confirm')
  })

  it('shipped: earlier checkpoints done with their real times, delivery still ahead', () => {
    const steps = buildTrackingSteps({
      status: 'SHIPPED',
      confirmedAt: CONFIRMED,
      history: [at('PENDING', PLACED), at('CONFIRMED', CONFIRMED), at('PACKED', PACKED), at('SHIPPED', SHIPPED)],
    })
    expect(steps.map((step) => [step.key, step.state, step.at])).toEqual([
      ['PENDING', 'done', PLACED],
      ['CONFIRMED', 'done', CONFIRMED],
      ['PACKED', 'done', PACKED],
      ['SHIPPED', 'current', SHIPPED],
      ['DELIVERED', 'upcoming', null],
    ])
  })

  it('delivered: the whole route is done', () => {
    const steps = buildTrackingSteps({
      status: 'DELIVERED',
      confirmedAt: CONFIRMED,
      history: [at('PENDING', PLACED), at('CONFIRMED', CONFIRMED), at('PACKED', PACKED), at('SHIPPED', SHIPPED), at('DELIVERED', DELIVERED)],
    })
    expect(steps.every((step) => step.state === 'done')).toBe(true)
  })

  it('cancelled while pending: only what really happened, no invented checkpoints', () => {
    const steps = buildTrackingSteps({
      status: 'CANCELLED', confirmedAt: null, history: [at('PENDING', PLACED), at('CANCELLED', CANCELLED)],
    })
    expect(steps.map((step) => [step.key, step.state])).toEqual([['PENDING', 'done'], ['CANCELLED', 'cancelled']])
    expect(steps[1]?.note).toBe('Reservation released')
  })

  it('cancelled after confirmation: shows the confirmation and that items were restocked', () => {
    const steps = buildTrackingSteps({
      status: 'CANCELLED',
      confirmedAt: CONFIRMED,
      history: [at('PENDING', PLACED), at('CONFIRMED', CONFIRMED), at('CANCELLED', CANCELLED)],
    })
    expect(steps.map((step) => step.key)).toEqual(['PENDING', 'CONFIRMED', 'CANCELLED'])
    expect(steps[2]?.note).toBe('Items returned to stock')
  })

  it('old orders without history still render a sensible route', () => {
    const steps = buildTrackingSteps({ status: 'CONFIRMED', confirmedAt: CONFIRMED, history: [] })
    expect(steps.map((step) => step.state)).toEqual(['done', 'current', 'upcoming', 'upcoming', 'upcoming'])
  })
})

describe('cancellation window', () => {
  const confirmedMs = Date.parse(CONFIRMED)
  const confirmed = { status: 'CONFIRMED' as const, confirmedAt: CONFIRMED }

  it('pending orders can always be cancelled', () => {
    expect(canCancel({ status: 'PENDING', confirmedAt: null }, Date.parse('2030-01-01T00:00:00Z'))).toBe(true)
  })

  it('confirmed orders count down 5 minutes and close exactly at the end', () => {
    expect(cancelWindowRemaining(confirmed, confirmedMs + 60_000)).toBe(CONFIRMED_CANCEL_WINDOW_MS - 60_000)
    expect(canCancel(confirmed, confirmedMs + CONFIRMED_CANCEL_WINDOW_MS - 1)).toBe(true)
    expect(canCancel(confirmed, confirmedMs + CONFIRMED_CANCEL_WINDOW_MS)).toBe(false)
  })

  it('never shows more than the window when the browser clock is behind the server', () => {
    expect(cancelWindowRemaining(confirmed, confirmedMs - 3_000)).toBe(CONFIRMED_CANCEL_WINDOW_MS)
  })

  it('packed orders cannot be cancelled, even inside the 5 minutes', () => {
    expect(canCancel({ status: 'PACKED', confirmedAt: CONFIRMED }, confirmedMs + 1_000)).toBe(false)
  })

  it('cancelled orders cannot be cancelled again', () => {
    expect(canCancel({ status: 'CANCELLED', confirmedAt: CONFIRMED }, confirmedMs)).toBe(false)
  })
})

describe('nextFulfilmentAction', () => {
  it('offers exactly one next step through the warehouse', () => {
    expect(nextFulfilmentAction('PENDING')).toBeNull()
    expect(nextFulfilmentAction('CONFIRMED')).toBe('pack')
    expect(nextFulfilmentAction('PACKED')).toBe('ship')
    expect(nextFulfilmentAction('SHIPPED')).toBe('deliver')
    expect(nextFulfilmentAction('DELIVERED')).toBeNull()
    expect(nextFulfilmentAction('CANCELLED')).toBeNull()
  })
})

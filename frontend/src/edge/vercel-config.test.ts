// @vitest-environment node
import { describe, expect, it } from 'vitest'

import vercelJson from '../../vercel.json'

// vercel.json routing is easy to get subtly wrong and only shows up after a deploy, so the rules are pinned here.
type Header = { key: string; value: string }
type Rule = { source: string; headers: Header[] }
type Config = { rewrites: { source: string; destination: string }[]; headers: Rule[] }

const config = vercelJson as Config

// Vercel sources are path-to-regexp patterns; ours are plain regex groups, so anchoring them is enough.
const matches = (source: string, path: string) => new RegExp(`^${source}$`).test(path)
const cacheControlFor = (path: string) =>
  config.headers
    .filter((rule) => matches(rule.source, path))
    .flatMap((rule) => rule.headers)
    .filter((header) => header.key.toLowerCase() === 'cache-control')
    .map((header) => header.value)

describe('vercel.json', () => {
  it('never sets Cache-Control on API responses (Spring sends no-store for private data)', () => {
    expect(cacheControlFor('/api/v1/orders')).toEqual([])
    expect(cacheControlFor('/api/v1/auth/me')).toEqual([])
  })

  it('caches hashed assets for a year and revalidates every page', () => {
    expect(cacheControlFor('/assets/index-abc123.js')).toEqual(['public, max-age=31536000, immutable'])
    expect(cacheControlFor('/')).toEqual(['no-cache'])
    expect(cacheControlFor('/orders/42')).toEqual(['no-cache'])
  })

  it('sends pages to the app, but never API calls or missing assets', () => {
    const spa = config.rewrites.find((rewrite) => rewrite.destination === '/index.html')
    expect(spa).toBeDefined()
    expect(matches(spa!.source, '/orders/42')).toBe(true)
    expect(matches(spa!.source, '/api/v1/catalog')).toBe(false)
    expect(matches(spa!.source, '/assets/missing.js')).toBe(false)
  })
})

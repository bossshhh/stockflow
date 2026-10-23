import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/auth/token-store'
import { authFetch } from './client'

const ORIGIN = 'http://localhost:5173'

describe('authFetch', () => {
  beforeEach(() => setAccessToken('expired-token'))
  afterEach(() => vi.unstubAllGlobals())

  it('sends the bearer token', async () => {
    const fetchMock = vi.fn(async (_request: Request) => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await authFetch(new Request(`${ORIGIN}/api/v1/orders`))

    expect(fetchMock.mock.calls[0]?.[0].headers.get('Authorization')).toBe('Bearer expired-token')
  })

  it('on 401 refreshes once and retries with the new token', async () => {
    const seen: string[] = []
    vi.stubGlobal('fetch', vi.fn(async (input: Request | string) => {
      const url = typeof input === 'string' ? input : input.url
      if (url.endsWith('/api/v1/auth/refresh')) {
        return new Response(JSON.stringify({ accessToken: 'fresh-token', tokenType: 'Bearer', expiresIn: 900 }), { status: 200 })
      }
      const request = input as Request
      seen.push(request.headers.get('Authorization') ?? '')
      return new Response('{}', { status: seen.length === 1 ? 401 : 200 })
    }))

    const response = await authFetch(new Request(`${ORIGIN}/api/v1/orders`))

    expect(response.status).toBe(200)
    expect(seen).toEqual(['Bearer expired-token', 'Bearer fresh-token'])
  })

  it('does not retry a failed login (401 there means wrong password, not an expired token)', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await authFetch(new Request(`${ORIGIN}/api/v1/auth/login`, { method: 'POST' }))

    expect(response.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('returns the original 401 when the session cannot be refreshed', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 401 })))

    const response = await authFetch(new Request(`${ORIGIN}/api/v1/orders`))

    expect(response.status).toBe(401)
  })
})

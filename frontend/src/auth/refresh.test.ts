import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { refreshAccessToken } from './refresh'
import { getAccessToken, setAccessToken } from './token-store'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('refreshAccessToken', () => {
  beforeEach(() => setAccessToken(null))
  afterEach(() => vi.unstubAllGlobals())

  it('shares one request between concurrent callers (the backend would treat a second as token theft)', async () => {
    let resolveFetch: (response: Response) => void = () => undefined
    const fetchMock = vi.fn(() => new Promise<Response>((resolve) => { resolveFetch = resolve }))
    vi.stubGlobal('fetch', fetchMock)

    const calls = [refreshAccessToken(), refreshAccessToken(), refreshAccessToken()]
    resolveFetch(jsonResponse(200, { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 900 }))

    await expect(Promise.all(calls)).resolves.toEqual(['new-token', 'new-token', 'new-token'])
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBe('new-token')
  })

  it('starts a new request once the previous one has finished', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, { accessToken: 'token', tokenType: 'Bearer', expiresIn: 900 }))
    vi.stubGlobal('fetch', fetchMock)

    await refreshAccessToken()
    await refreshAccessToken()

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('signs out (null token) when the session is no longer valid', async () => {
    setAccessToken('old-token')
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(401, { code: 'INVALID_REFRESH_TOKEN' })))

    await expect(refreshAccessToken()).resolves.toBeNull()
    expect(getAccessToken()).toBeNull()
  })

  it('keeps the current token when the network is down (a blip is not a logout)', async () => {
    setAccessToken('still-valid')
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))

    await expect(refreshAccessToken()).rejects.toThrow()
    expect(getAccessToken()).toBe('still-valid')
  })

  it('rejects a response without an access token instead of storing garbage', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(200, { unexpected: true })))

    await expect(refreshAccessToken()).rejects.toThrow('did not contain an access token')
  })
})

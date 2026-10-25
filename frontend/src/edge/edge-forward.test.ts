// @vitest-environment node
import { describe, expect, it } from 'vitest'

import { edgeForward, type EdgeEnv } from './edge-forward'

const ENV = { apiOrigin: 'https://stockflow-shop-api.vercel.app', secret: 'edge-secret-0123456789abcdef-0123456789' }

const IP = '203.0.113.5'

function forwarded(request: Request, clientIp: string | undefined, env = ENV) {
  const result = edgeForward(request, clientIp, env)
  if ('error' in result) throw new Error(`expected a forward, got ${result.error.status}`)
  return result
}

describe('edgeForward', () => {
  it('forwards path and query to the API', () => {
    const { url } = forwarded(new Request('https://app.vercel.app/api/v1/catalog?page=2&q=a%20b'), IP)
    expect(url.toString()).toBe('https://stockflow-shop-api.vercel.app/api/v1/catalog?page=2&q=a%20b')
  })

  it('keeps a base path and ignores a trailing slash', () => {
    const { url } = forwarded(new Request('https://app.vercel.app/api/v1/catalog'), IP, {
      ...ENV,
      apiOrigin: 'https://httpbin.org/anything/',
    })
    expect(url.toString()).toBe('https://httpbin.org/anything/api/v1/catalog')
  })

  it('adds the secret and the client IP', () => {
    const { headers } = forwarded(new Request('https://app.vercel.app/api/v1/catalog'), IP)
    expect(headers.get('x-stockflow-edge-secret')).toBe(ENV.secret)
    expect(headers.get('x-stockflow-client-ip')).toBe('203.0.113.5')
  })

  it('drops StockFlow headers sent by the browser', () => {
    const request = new Request('https://app.vercel.app/api/v1/auth/login', {
      headers: { 'X-StockFlow-Client-IP': '1.1.1.1', 'x-STOCKFLOW-edge-secret': 'guess', 'X-StockFlow-Other': 'x' },
    })
    const { headers } = forwarded(request, IP)
    expect(headers.get('x-stockflow-client-ip')).toBe('203.0.113.5')
    expect(headers.get('x-stockflow-edge-secret')).toBe(ENV.secret)
    expect(headers.has('x-stockflow-other')).toBe(false)
  })

  it('keeps other headers', () => {
    const request = new Request('https://app.vercel.app/api/v1/orders', {
      method: 'POST',
      headers: {
        authorization: 'Bearer abc',
        cookie: 'stockflow_refresh=r',
        'idempotency-key': 'k-1',
        'content-type': 'application/json',
      },
    })
    const { headers } = forwarded(request, IP)
    expect(headers.get('authorization')).toBe('Bearer abc')
    expect(headers.get('cookie')).toBe('stockflow_refresh=r')
    expect(headers.get('idempotency-key')).toBe('k-1')
    expect(headers.get('content-type')).toBe('application/json')
  })

  it('sends no client IP header when the IP is unknown', () => {
    const { headers } = forwarded(new Request('https://app.vercel.app/api/v1/catalog'), undefined)
    expect(headers.has('x-stockflow-client-ip')).toBe(false)
    expect(headers.get('x-stockflow-edge-secret')).toBe(ENV.secret)
  })

  it.each<EdgeEnv>([
    { apiOrigin: undefined, secret: ENV.secret },
    { apiOrigin: '', secret: ENV.secret },
    { apiOrigin: ENV.apiOrigin, secret: undefined },
    { apiOrigin: ENV.apiOrigin, secret: '' },
  ])('fails closed with 503 when configuration is missing (%o)', async (env) => {
    const result = edgeForward(new Request('https://app.vercel.app/api/v1/catalog'), '203.0.113.5', env)
    if (!('error' in result)) throw new Error('expected an error response')
    expect(result.error.status).toBe(503)
    expect(await result.error.json()).toMatchObject({ code: 'SERVICE_UNAVAILABLE' })
  })
})

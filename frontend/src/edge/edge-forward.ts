// Free-tier deployment (UI and API as two Vercel projects): decides where an /api request goes and
// with which headers.
// Kept free of Vercel imports so it can be unit tested; middleware.ts is only the glue.

export const EDGE_SECRET_HEADER = 'X-StockFlow-Edge-Secret'
export const CLIENT_IP_HEADER = 'X-StockFlow-Client-IP'

// Every X-StockFlow-* header from the browser is dropped: only this code may set them, or a visitor could
// claim any IP (and the backend would believe it, since the request does carry our secret).
const OUR_HEADER_PREFIX = 'x-stockflow-'

export type EdgeEnv = { apiOrigin?: string; secret?: string }
export type EdgeForward = { url: URL; headers: Headers } | { error: Response }

export function edgeForward(request: Request, clientIp: string | undefined, env: EdgeEnv): EdgeForward {
  // Fail closed: never forward without the secret, and never to "undefined/api/...".
  if (!env.apiOrigin || !env.secret) {
    return {
      error: Response.json(
        { status: 503, code: 'SERVICE_UNAVAILABLE', message: 'The API is not configured' },
        { status: 503 },
      ),
    }
  }
  const incoming = new URL(request.url)
  const url = new URL(env.apiOrigin.replace(/\/+$/, '') + incoming.pathname + incoming.search)

  const headers = new Headers()
  request.headers.forEach((value, name) => {
    if (!name.toLowerCase().startsWith(OUR_HEADER_PREFIX)) headers.set(name, value)
  })
  headers.set(EDGE_SECRET_HEADER, env.secret)
  if (clientIp) headers.set(CLIENT_IP_HEADER, clientIp)
  return { url, headers }
}

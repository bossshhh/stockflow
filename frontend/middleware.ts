// Vercel Routing Middleware (free-tier deployment only; local dev uses the Vite proxy, Docker uses nginx).
// Forwards /api to the Spring Boot API project (a Vercel container) with the visitor's IP and the shared edge secret, so the
// browser keeps seeing ONE origin (the SameSite=Strict refresh cookie keeps working). Logic: src/edge.
import { ipAddress, rewrite } from '@vercel/functions'

import { edgeForward } from './src/edge/edge-forward.js'

export const config = { matcher: '/api/:path*' }

export default function middleware(request: Request): Response {
  const result = edgeForward(request, ipAddress(request), {
    apiOrigin: process.env.STOCKFLOW_API_ORIGIN,
    secret: process.env.STOCKFLOW_EDGE_SECRET,
  })
  if ('error' in result) return result.error
  return rewrite(result.url, { request: { headers: result.headers } })
}

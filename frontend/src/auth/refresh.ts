import { setAccessToken } from './token-store'

export const REFRESH_PATH = '/api/v1/auth/refresh'
const LOCK_NAME = 'stockflow-token-refresh'

/**
 * Exchanges the HttpOnly refresh cookie for a new access token.
 *
 * Why it is guarded twice: the backend rotates the refresh token on every use and treats a second use
 * of the same token as theft (it revokes the whole session). So refreshes must never overlap:
 *  1. Within one tab, concurrent callers share ONE in-flight promise.
 *  2. Across tabs, a Web Lock makes tabs take turns. The browser stores the rotated cookie before the
 *     lock is released, so the next tab presents the NEW cookie and the backend sees no reuse.
 *
 * Resolves to the new token, or null when there is no valid session (the user must sign in).
 */
let inFlight: Promise<string | null> | null = null

export function refreshAccessToken(): Promise<string | null> {
  if (inFlight === null) {
    inFlight = withCrossTabLock(requestNewToken).finally(() => {
      inFlight = null
    })
  }
  return inFlight
}

async function withCrossTabLock(task: () => Promise<string | null>): Promise<string | null> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined
  if (locks === undefined) {
    // Very old browsers: per-tab single flight still applies.
    return task()
  }
  return locks.request(LOCK_NAME, task)
}

async function requestNewToken(): Promise<string | null> {
  let response: Response
  try {
    response = await fetch(REFRESH_PATH, { method: 'POST', credentials: 'same-origin' })
  } catch {
    // Network failure: keep whatever token we had; do not log the user out for a blip.
    throw new Error('Cannot reach the server to refresh the session')
  }
  if (!response.ok) {
    setAccessToken(null)
    return null
  }
  const body: unknown = await response.json()
  const token = readAccessToken(body)
  setAccessToken(token)
  return token
}

function readAccessToken(body: unknown): string {
  if (typeof body === 'object' && body !== null && 'accessToken' in body) {
    const token = (body as { accessToken: unknown }).accessToken
    if (typeof token === 'string' && token.length > 0) {
      return token
    }
  }
  throw new Error('Refresh response did not contain an access token')
}

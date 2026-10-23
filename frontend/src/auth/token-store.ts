/**
 * The access token lives ONLY in memory (this module), never in localStorage or sessionStorage:
 * an injected script could read storage, but cannot reach the refresh token (HttpOnly cookie) and only
 * gets a token that expires within 15 minutes. A page reload loses it; the session is then restored
 * from the refresh cookie.
 */
type Listener = (token: string | null) => void

let accessToken: string | null = null
const listeners = new Set<Listener>()

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  if (token !== null && token.trim() === '') {
    throw new Error('Access token must not be blank')
  }
  accessToken = token
  listeners.forEach((listener) => listener(token))
}

export function onAccessTokenChange(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

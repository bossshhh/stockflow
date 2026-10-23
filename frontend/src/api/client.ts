import createClient from 'openapi-fetch'

import { REFRESH_PATH, refreshAccessToken } from '@/auth/refresh'
import { getAccessToken } from '@/auth/token-store'
import { ApiRequestError, isApiErrorBody } from './errors'
import type { paths } from './schema'

const AUTH_PATH_PREFIX = '/api/v1/auth/'
/** Auth endpoints that answer 401 for bad credentials; retrying them after a refresh makes no sense. */
const NO_RETRY_PATHS = new Set([REFRESH_PATH, `${AUTH_PATH_PREFIX}login`, `${AUTH_PATH_PREFIX}logout`])

/**
 * fetch with authentication:
 *  - adds "Authorization: Bearer <access token>" when we have one;
 *  - on 401, refreshes the access token ONCE (single-flight, cross-tab locked) and retries the request.
 */
export async function authFetch(request: Request): Promise<Response> {
  const retry = request.clone()
  const response = await send(request)
  if (response.status !== 401 || NO_RETRY_PATHS.has(new URL(request.url).pathname)) {
    return response
  }
  const newToken = await refreshAccessToken()
  if (newToken === null) {
    return response
  }
  return send(retry)
}

async function send(request: Request): Promise<Response> {
  const token = getAccessToken()
  if (token !== null) {
    request.headers.set('Authorization', `Bearer ${token}`)
  }
  try {
    return await fetch(request)
  } catch {
    throw new ApiRequestError(0, undefined)
  }
}

/** Typed client generated from the backend's OpenAPI spec: wrong paths, params or bodies fail to compile. */
export const api = createClient<paths>({
  baseUrl: window.location.origin,
  fetch: authFetch,
})

type ApiResult<T> = { data?: T; error?: unknown; response: Response }

/**
 * Turns an openapi-fetch result into data-or-throw, which is what TanStack Query expects.
 * Throws ApiRequestError carrying the backend's ApiError (status, code, field errors).
 */
export async function unwrap<T>(call: Promise<ApiResult<T>>): Promise<T> {
  const { data, error, response } = await call
  if (!response.ok) {
    throw new ApiRequestError(response.status, isApiErrorBody(error) ? error : undefined)
  }
  return data as T
}

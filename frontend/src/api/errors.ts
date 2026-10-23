import type { components } from './schema'

export type ApiErrorBody = components['schemas']['ApiError']
export type FieldViolation = components['schemas']['FieldViolation']

/**
 * Every failed API call becomes one of these. `code` is the backend's stable error code
 * (e.g. INSUFFICIENT_STOCK), so the UI branches on codes, never on message text.
 */
export class ApiRequestError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldViolation[]

  constructor(status: number, body: ApiErrorBody | undefined) {
    super(body?.message ?? fallbackMessage(status))
    this.name = 'ApiRequestError'
    this.status = status
    this.code = body?.code ?? (status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN_ERROR')
    this.fieldErrors = body?.fieldErrors ?? []
  }
}

function fallbackMessage(status: number): string {
  if (status === 0) return 'Cannot reach the server. Check your connection and try again.'
  if (status >= 500) return 'The server had a problem. Try again in a moment.'
  return `Request failed (${status}).`
}

/** Narrowing guard for an ApiError-shaped JSON body coming from an untyped source. */
export function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.code === 'string' && typeof candidate.message === 'string'
    && typeof candidate.status === 'number' && Array.isArray(candidate.fieldErrors)
}

/** A short, user-facing message for any thrown value. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message
  if (error instanceof Error) return error.message
  return 'Something went wrong.'
}

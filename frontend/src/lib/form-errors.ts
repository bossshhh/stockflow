import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'

import { ApiRequestError } from '@/api/errors'

/**
 * Copies the backend's per-field validation errors onto the form, so server-side rules show up next to
 * the right input. Returns true when at least one field error was applied.
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly Path<T>[],
): boolean {
  if (!(error instanceof ApiRequestError) || error.fieldErrors.length === 0) {
    return false
  }
  let applied = false
  for (const violation of error.fieldErrors) {
    const field = knownFields.find((name) => name === violation.field)
    if (field !== undefined) {
      setError(field, { type: 'server', message: violation.message })
      applied = true
    }
  }
  return applied
}

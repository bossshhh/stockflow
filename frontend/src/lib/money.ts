/** 19.9 * 100 is 1989.9999999999998 in floating point, so compare with a tolerance, not ===. */
export function hasAtMostTwoDecimals(value: number): boolean {
  if (!Number.isFinite(value)) return false
  const cents = value * 100
  return Math.abs(cents - Math.round(cents)) < 1e-6
}

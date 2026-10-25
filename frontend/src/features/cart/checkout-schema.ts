import { z } from 'zod'

// Line breaks, control and invisible format characters (BOM, zero-width, bidi overrides) would end up on a
// printed shipping label (same Unicode categories as the server: Cc, Cf, Zl, Zp).
const NO_CONTROL_CHARACTERS = /^[^\p{Cc}\p{Cf}\p{Zl}\p{Zp}]*$/u
const text = (label: string, max: number) => z.string().trim()
  .min(1, `Enter the ${label}`)
  .max(max, `Use at most ${max} characters`)
  .regex(NO_CONTROL_CHARACTERS, 'Remove line breaks')

/**
 * Same rules as the backend's ShippingAddressRequest (DHL's field limits); the backend re-checks
 * everything. Germany only for now, so there is no country field: the server sets it.
 */
export const checkoutSchema = z.object({
  shippingAddress: z.object({
    name: text('name', 50),
    street: text('street', 50),
    houseNumber: text('house number', 10),
    // JS \d is ASCII-only, like the server's: other digit scripts are rejected on both sides.
    postcode: z.string().trim().regex(/^\d{5}$/, 'Enter a German postcode (5 digits)'),
    city: text('city', 40),
  }),
})
export type CheckoutValues = z.infer<typeof checkoutSchema>
export const ADDRESS_FIELDS = [
  'shippingAddress.name',
  'shippingAddress.street',
  'shippingAddress.houseNumber',
  'shippingAddress.postcode',
  'shippingAddress.city',
] as const

import type { FieldErrors, UseFormRegister } from 'react-hook-form'

import { Field } from '@/components/form'
import { Input } from '@/components/ui/input'
import type { CheckoutValues } from './checkout-schema'

export function ShippingAddressForm({ register, errors }: {
  register: UseFormRegister<CheckoutValues>
  errors: FieldErrors<CheckoutValues>
}) {
  const fieldErrors = errors.shippingAddress
  return (
    <fieldset className="grid gap-4 rounded-xl border border-border bg-panel p-5">
      <legend className="px-1 font-heading text-lg">Ship to</legend>
      <Field id="shippingAddress.name" label="Full name" error={fieldErrors?.name?.message}>
        <Input id="shippingAddress.name" autoComplete="shipping name" aria-invalid={fieldErrors?.name !== undefined} {...register('shippingAddress.name')} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-[1fr_8rem]">
        <Field id="shippingAddress.street" label="Street" error={fieldErrors?.street?.message}>
          <Input id="shippingAddress.street" autoComplete="shipping address-line1" aria-invalid={fieldErrors?.street !== undefined} {...register('shippingAddress.street')} />
        </Field>
        <Field id="shippingAddress.houseNumber" label="House number" error={fieldErrors?.houseNumber?.message}>
          <Input id="shippingAddress.houseNumber" autoComplete="shipping address-line2" aria-invalid={fieldErrors?.houseNumber !== undefined} {...register('shippingAddress.houseNumber')} />
        </Field>
      </div>
      <div className="grid gap-4 sm:grid-cols-[8rem_1fr]">
        <Field id="shippingAddress.postcode" label="Postcode" error={fieldErrors?.postcode?.message}>
          <Input id="shippingAddress.postcode" inputMode="numeric" autoComplete="shipping postal-code" maxLength={5} aria-invalid={fieldErrors?.postcode !== undefined} {...register('shippingAddress.postcode')} />
        </Field>
        <Field id="shippingAddress.city" label="City" error={fieldErrors?.city?.message}>
          <Input id="shippingAddress.city" autoComplete="shipping address-level2" aria-invalid={fieldErrors?.city !== undefined} {...register('shippingAddress.city')} />
        </Field>
      </div>
      <Field id="shippingAddress.country" label="Country" hint="We ship within Germany only for now.">
        <Input id="shippingAddress.country" value="Germany" readOnly aria-readonly="true" tabIndex={-1} />
      </Field>
    </fieldset>
  )
}

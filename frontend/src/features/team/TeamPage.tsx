import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'

import { api, unwrap } from '@/api/client'
import { errorMessage } from '@/api/errors'
import { Field } from '@/components/form'
import { applyServerFieldErrors } from '@/lib/form-errors'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

/** Mirrors CreateUserRequest; customers register themselves, so only WAREHOUSE, STAFF and ADMIN are created here. */
const schema = z.object({
  fullName: z.string().trim().min(1, 'Enter a name').max(200),
  email: z.email('Enter a valid email').max(254),
  password: z.string().min(12, 'Use at least 12 characters')
    .refine((value) => new TextEncoder().encode(value).length <= 72, 'Use at most 72 bytes'),
  role: z.enum(['WAREHOUSE', 'STAFF', 'ADMIN']),
})
type Values = z.infer<typeof schema>
const FIELDS = ['fullName', 'email', 'password', 'role'] as const

export function TeamPage() {
  const { register, handleSubmit, control, reset, setError, formState: { errors } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', email: '', password: '', role: 'STAFF' },
  })
  const create = useMutation({
    mutationFn: (values: Values) => unwrap(api.POST('/api/v1/admin/users', { body: values })),
    onSuccess: (user) => {
      toast.success(`${user.fullName} can now sign in (${user.role.toLowerCase()})`)
      reset()
    },
    onError: (error) => {
      if (!applyServerFieldErrors(error, setError, FIELDS)) toast.error(errorMessage(error))
    },
  })

  return (
    <>
      <PageHeader title="Team" description="Give your team their own sign-in. Share the password with them privately; they can't change it yet." />
      <form noValidate onSubmit={handleSubmit((values) => create.mutate(values))} className="grid max-w-md gap-4 rounded-xl border border-border bg-panel p-5">
        <Field id="fullName" label="Full name" error={errors.fullName?.message}>
          <Input id="fullName" aria-invalid={errors.fullName !== undefined} {...register('fullName')} />
        </Field>
        <Field id="email" label="Email" error={errors.email?.message}>
          <Input id="email" type="email" aria-invalid={errors.email !== undefined} {...register('email')} />
        </Field>
        <Field id="password" label="Temporary password" error={errors.password?.message} hint="At least 12 characters.">
          <Input id="password" type="password" autoComplete="new-password" aria-invalid={errors.password !== undefined} {...register('password')} />
        </Field>
        <Field id="role" label="Role" error={errors.role?.message}>
          <Controller
            control={control}
            name="role"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="role" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="WAREHOUSE">Warehouse: pack, ship and deliver orders</SelectItem>
                  <SelectItem value="STAFF">Staff: products, stock and orders</SelectItem>
                  <SelectItem value="ADMIN">Admin: staff, plus managing the team</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </Field>
        <Button type="submit" disabled={create.isPending}>{create.isPending ? 'Creating…' : 'Create account'}</Button>
      </form>
    </>
  )
}

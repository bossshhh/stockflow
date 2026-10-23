import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'

import { api, unwrap } from '@/api/client'
import { errorMessage } from '@/api/errors'
import { useAuth } from '@/auth/useAuth'
import { Field } from '@/components/form'
import { applyServerFieldErrors } from '@/lib/form-errors'
import { homePathFor } from '@/components/layout/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { AuthCard } from './AuthCard'

/** Same rules as the backend's RegisterRequest; the backend re-checks everything. */
const schema = z.object({
  fullName: z.string().trim().min(1, 'Enter your name').max(200),
  email: z.email('Enter a valid email').max(254),
  password: z.string()
    .min(12, 'Use at least 12 characters')
    .refine((value) => new TextEncoder().encode(value).length <= 72, 'Use at most 72 bytes'),
})
type Values = z.infer<typeof schema>
const FIELDS = ['fullName', 'email', 'password'] as const

export function RegisterPage() {
  const { state, signIn } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
  })

  if (state.status === 'authenticated') {
    return <Navigate to={homePathFor(state.user)} replace />
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await unwrap(api.POST('/api/v1/auth/register', { body: values }))
      const user = await signIn(values.email, values.password)
      toast.success('Account created')
      navigate(homePathFor(user), { replace: true })
    } catch (error) {
      if (!applyServerFieldErrors(error, setError, FIELDS)) {
        setFormError(errorMessage(error))
      }
    }
  })

  return (
    <AuthCard title="Create an account" footer={<>Already registered? <Link className="text-primary underline-offset-4 hover:underline" to="/login">Sign in</Link></>}>
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <Field id="fullName" label="Full name" error={errors.fullName?.message}>
          <Input id="fullName" autoComplete="name" autoFocus aria-invalid={errors.fullName !== undefined} {...register('fullName')} />
        </Field>
        <Field id="email" label="Email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" aria-invalid={errors.email !== undefined} {...register('email')} />
        </Field>
        <Field id="password" label="Password" error={errors.password?.message} hint="At least 12 characters. A short sentence works well.">
          <Input id="password" type="password" autoComplete="new-password" aria-invalid={errors.password !== undefined} {...register('password')} />
        </Field>
        {formError !== null && <p className="text-sm text-status-cancelled" role="alert">{formError}</p>}
        <Button type="submit" size="lg" disabled={isSubmitting}>{isSubmitting ? 'Creating account…' : 'Create account'}</Button>
      </form>
    </AuthCard>
  )
}

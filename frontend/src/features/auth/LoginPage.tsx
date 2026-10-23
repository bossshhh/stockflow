import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'

import { errorMessage } from '@/api/errors'
import { useAuth } from '@/auth/useAuth'
import { Field } from '@/components/form'
import { homePathFor } from '@/components/layout/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { AuthCard } from './AuthCard'

const schema = z.object({
  email: z.string().trim().min(1, 'Enter your email').max(254),
  password: z.string().min(1, 'Enter your password').max(72),
})
type Values = z.infer<typeof schema>

export function LoginPage() {
  const { state, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
  })

  if (state.status === 'authenticated') {
    return <Navigate to={homePathFor(state.user)} replace />
  }

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFormError(null)
    try {
      const user = await signIn(email, password)
      navigate(safeRedirect(location.state) ?? homePathFor(user), { replace: true })
    } catch (error) {
      setFormError(errorMessage(error))
    }
  })

  return (
    <AuthCard title="Sign in" footer={<>New here? <Link className="text-primary underline-offset-4 hover:underline" to="/register">Create an account</Link></>}>
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <Field id="email" label="Email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" autoFocus aria-invalid={errors.email !== undefined} {...register('email')} />
        </Field>
        <Field id="password" label="Password" error={errors.password?.message}>
          <Input id="password" type="password" autoComplete="current-password" aria-invalid={errors.password !== undefined} {...register('password')} />
        </Field>
        {formError !== null && <p className="text-sm text-status-cancelled" role="alert">{formError}</p>}
        <Button type="submit" size="lg" disabled={isSubmitting}>{isSubmitting ? 'Signing in…' : 'Sign in'}</Button>
      </form>
    </AuthCard>
  )
}

/** Only follow in-app redirects ("/orders"), never absolute URLs (open-redirect protection). */
function safeRedirect(routeState: unknown): string | null {
  if (typeof routeState !== 'object' || routeState === null || !('from' in routeState)) return null
  const from = (routeState as { from: unknown }).from
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : null
}

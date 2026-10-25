import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'

import { api, unwrap } from '@/api/client'
import { ApiRequestError, errorMessage } from '@/api/errors'
import { useAuth } from '@/auth/useAuth'
import { cart, cartTotal, useCart } from '@/cart/cart-store'
import { checkoutKey } from '@/cart/checkout-key'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states'
import { Button } from '@/components/ui/button'
import { QuantityStepper } from '@/features/shop/ShopPage'
import { applyServerFieldErrors } from '@/lib/form-errors'
import { formatMoney, orderReference } from '@/lib/format'
import { ADDRESS_FIELDS, checkoutSchema, type CheckoutValues } from './checkout-schema'
import { ShippingAddressForm } from './ShippingAddressForm'

const CHECKOUT_FORM = 'checkout-form'

export function CartPage() {
  const lines = useCart()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { state } = useAuth()
  const { register, handleSubmit, setError, formState: { errors } } = useForm<CheckoutValues>({
    resolver: zodResolver(checkoutSchema),
    defaultValues: {
      shippingAddress: {
        name: state.status === 'authenticated' ? state.user.fullName : '',
        street: '', houseNumber: '', postcode: '', city: '',
      },
    },
  })

  const placeOrder = useMutation({
    mutationFn: ({ shippingAddress }: CheckoutValues) => {
      const items = lines.map((line) => ({ productId: line.productId, quantity: line.quantity }))
      // Same cart and address, same key: a retry returns the order already placed instead of placing it twice.
      return unwrap(api.POST('/api/v1/orders', {
        params: { header: { 'Idempotency-Key': checkoutKey.keyFor(items, shippingAddress) } },
        body: { items, shippingAddress },
      }))
    },
    onSuccess: (order) => {
      checkoutKey.reset()
      cart.clear()
      void queryClient.invalidateQueries({ queryKey: ['orders'] })
      void queryClient.invalidateQueries({ queryKey: ['catalog'] })
      toast.success(`Order ${orderReference(order.id)} placed`)
      navigate(`/orders/${order.id}`)
    },
    onError: (error) => {
      // A server-side address rule shows up next to its input; anything else is a toast.
      if (!applyServerFieldErrors(error, setError, ADDRESS_FIELDS)) {
        toast.error(checkoutMessage(error))
      }
    },
  })
  const onSubmit = handleSubmit((values) => placeOrder.mutate(values))

  if (lines.length === 0) {
    return (
      <>
        <PageHeader title="Cart" />
        <EmptyState title="Your cart is empty">
          <Link className="text-primary underline-offset-4 hover:underline" to="/shop">Browse the shop</Link> to add products.
        </EmptyState>
      </>
    )
  }

  return (
    <>
      <PageHeader title="Cart" description="Prices and stock are confirmed when you place the order." />
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="grid h-fit gap-6">
          <ul className="divide-y divide-border rounded-xl border border-border bg-panel">
            {lines.map((line) => (
              <li key={line.productId} className="flex flex-wrap items-center gap-4 px-4 py-4">
                <div className="min-w-0 flex-1">
                  <p className="font-heading text-lg leading-tight">{line.name}</p>
                  <p className="text-sm text-muted-foreground">{line.sku} at {formatMoney(line.price)}</p>
                </div>
                <QuantityStepper value={line.quantity} onChange={(quantity) => cart.setQuantity(line.productId, quantity)} label={`Quantity of ${line.name}`} />
                <p className="w-28 text-right font-heading text-lg tabular-nums">{formatMoney(cartTotal([line]))}</p>
                <Button variant="ghost" size="icon-sm" aria-label={`Remove ${line.name}`} onClick={() => cart.remove(line.productId)}>
                  <Trash2 className="size-4" />
                </Button>
              </li>
            ))}
          </ul>
          <form id={CHECKOUT_FORM} onSubmit={onSubmit} noValidate>
            <ShippingAddressForm register={register} errors={errors} />
          </form>
        </div>

        <aside className="h-fit rounded-xl border border-border bg-panel p-5">
          <p className="text-sm text-muted-foreground">Total</p>
          <p className="font-heading text-4xl font-semibold tabular-nums">{formatMoney(cartTotal(lines))}</p>
          <Button type="submit" form={CHECKOUT_FORM} className="mt-5 w-full" size="lg" disabled={placeOrder.isPending}>
            {placeOrder.isPending ? 'Placing order…' : 'Place order'}
          </Button>
          <Button variant="ghost" className="mt-2 w-full" onClick={() => cart.clear()}>Empty cart</Button>
        </aside>
      </div>
    </>
  )
}

function checkoutMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 'INSUFFICIENT_STOCK':
        return `${error.message}. Lower the quantity or remove that product.`
      case 'PRODUCT_UNAVAILABLE':
      case 'PRODUCT_NOT_FOUND':
        return `${error.message}. Remove it from your cart to continue.`
      default:
        return error.message
    }
  }
  return errorMessage(error)
}

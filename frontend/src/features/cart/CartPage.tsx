import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'

import { api, unwrap } from '@/api/client'
import { ApiRequestError, errorMessage } from '@/api/errors'
import { cart, cartTotal, useCart } from '@/cart/cart-store'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states'
import { Button } from '@/components/ui/button'
import { QuantityStepper } from '@/features/shop/ShopPage'
import { formatMoney, orderReference } from '@/lib/format'

export function CartPage() {
  const lines = useCart()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const placeOrder = useMutation({
    mutationFn: () => unwrap(api.POST('/api/v1/orders', {
      body: { items: lines.map((line) => ({ productId: line.productId, quantity: line.quantity })) },
    })),
    onSuccess: (order) => {
      cart.clear()
      void queryClient.invalidateQueries({ queryKey: ['orders'] })
      void queryClient.invalidateQueries({ queryKey: ['catalog'] })
      toast.success(`Order ${orderReference(order.id)} placed`)
      navigate(`/orders/${order.id}`)
    },
    onError: (error) => {
      toast.error(checkoutMessage(error))
    },
  })

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

        <aside className="h-fit rounded-xl border border-border bg-panel p-5">
          <p className="text-sm text-muted-foreground">Total</p>
          <p className="font-heading text-4xl font-semibold tabular-nums">{formatMoney(cartTotal(lines))}</p>
          <Button className="mt-5 w-full" size="lg" disabled={placeOrder.isPending} onClick={() => placeOrder.mutate()}>
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

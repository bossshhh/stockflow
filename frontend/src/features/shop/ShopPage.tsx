import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Minus, Plus, Search } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'

import { api, unwrap } from '@/api/client'
import type { components } from '@/api/schema'
import { useCurrentUser } from '@/auth/useAuth'
import { cart, MAX_QUANTITY_PER_PRODUCT } from '@/cart/cart-store'
import { PageHeader } from '@/components/layout/PageHeader'
import { StockPill } from '@/components/orders/OrderStatusBadge'
import { Pagination } from '@/components/Pagination'
import { EmptyState, ErrorState, InlineSpinner } from '@/components/states'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatMoney } from '@/lib/format'
import { useDebouncedValue } from '@/lib/useDebouncedValue'

type CatalogItem = components['schemas']['CatalogItemResponse']

const PAGE_SIZE = 20
const SORTS = {
  'name,asc': 'Name A to Z',
  'price,asc': 'Price, low to high',
  'price,desc': 'Price, high to low',
} as const
type SortKey = keyof typeof SORTS

export function ShopPage() {
  const [search, setSearch] = useState('')
  const [sort, setSort] = useState<SortKey>('name,asc')
  const [page, setPage] = useState(0)
  const query = useDebouncedValue(search.trim(), 300)

  const catalog = useQuery({
    queryKey: ['catalog', { query, sort, page }],
    queryFn: () => unwrap(api.GET('/api/v1/catalog', {
      params: { query: { q: query === '' ? undefined : query.slice(0, 100), sort: [sort], page, size: PAGE_SIZE } },
    })),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader title="Shop" description="Everything in the catalog. Stock is reserved for you the moment you place an order." />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="relative min-w-60 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            aria-label="Search products by name"
            placeholder="Search by name"
            className="pl-9"
            maxLength={100}
            value={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0) }}
          />
        </div>
        <Select value={sort} onValueChange={(value) => { setSort(value as SortKey); setPage(0) }}>
          <SelectTrigger className="w-48" aria-label="Sort products"><SelectValue /></SelectTrigger>
          <SelectContent>
            {Object.entries(SORTS).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      {catalog.isPending ? <InlineSpinner label="Loading products" />
        : catalog.isError ? <ErrorState error={catalog.error} onRetry={() => catalog.refetch()} />
        : catalog.data.content.length === 0 ? (
          <EmptyState title={query === '' ? 'The catalog is empty' : `Nothing matches "${query}"`}>
            {query === '' ? 'Products appear here once staff add them.' : 'Try a shorter or different name.'}
          </EmptyState>
        ) : (
          <>
            <ul className="divide-y divide-border rounded-xl border border-border bg-panel">
              {catalog.data.content.map((item) => <CatalogRow key={item.id} item={item} />)}
            </ul>
            <Pagination page={catalog.data.page} totalPages={catalog.data.totalPages} totalElements={catalog.data.totalElements} onPageChange={setPage} />
          </>
        )}
    </>
  )
}

function CatalogRow({ item }: { item: CatalogItem }) {
  const user = useCurrentUser()
  const [quantity, setQuantity] = useState(1)

  const addToCart = () => {
    try {
      cart.add({ productId: item.id, sku: item.sku, name: item.name, price: item.price }, quantity)
      toast.success(`Added ${quantity} × ${item.name} to your cart`)
      setQuantity(1)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Could not add to cart')
    }
  }

  return (
    <li className="grid gap-3 px-4 py-4 sm:grid-cols-[1fr_auto] sm:items-center sm:gap-6">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <p className="font-heading text-lg leading-tight">{item.name}</p>
          <StockPill inStock={item.inStock} />
        </div>
        <p className="text-sm text-muted-foreground">{item.sku}</p>
        {item.description !== null && item.description !== undefined && (
          <p className="mt-1 max-w-prose text-sm">{item.description}</p>
        )}
      </div>
      <div className="flex items-center gap-4 sm:justify-end">
        <p className="font-heading text-xl tabular-nums">{formatMoney(item.price)}</p>
        {user === null ? (
          <Button asChild variant="outline" size="sm"><Link to="/login" state={{ from: '/shop' }}>Sign in to order</Link></Button>
        ) : user.role !== 'CUSTOMER' ? (
          <span className="text-xs text-muted-foreground">Customers order here</span>
        ) : (
          <div className="flex items-center gap-2">
            <QuantityStepper value={quantity} onChange={setQuantity} disabled={!item.inStock} label={`Quantity of ${item.name}`} />
            <Button size="sm" onClick={addToCart} disabled={!item.inStock}>Add to cart</Button>
          </div>
        )}
      </div>
    </li>
  )
}

export function QuantityStepper({ value, onChange, disabled = false, label }: {
  value: number
  onChange: (value: number) => void
  disabled?: boolean
  label: string
}) {
  const clamp = (next: number) => Math.min(MAX_QUANTITY_PER_PRODUCT, Math.max(1, Math.trunc(next)))
  return (
    <div className="flex items-center rounded-lg border border-input">
      <Button variant="ghost" size="icon-sm" aria-label="Decrease" disabled={disabled || value <= 1} onClick={() => onChange(clamp(value - 1))}>
        <Minus className="size-3.5" />
      </Button>
      <input
        aria-label={label}
        inputMode="numeric"
        className="h-7 w-12 bg-transparent text-center text-sm tabular-nums outline-none"
        value={value}
        disabled={disabled}
        onChange={(event) => {
          const parsed = Number.parseInt(event.target.value, 10)
          if (!Number.isNaN(parsed)) onChange(clamp(parsed))
        }}
      />
      <Button variant="ghost" size="icon-sm" aria-label="Increase" disabled={disabled || value >= MAX_QUANTITY_PER_PRODUCT} onClick={() => onChange(clamp(value + 1))}>
        <Plus className="size-3.5" />
      </Button>
    </div>
  )
}

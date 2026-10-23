import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'

import { api, unwrap } from '@/api/client'
import { errorMessage } from '@/api/errors'
import type { components } from '@/api/schema'
import { Field } from '@/components/form'
import { applyServerFieldErrors } from '@/lib/form-errors'
import { PageHeader } from '@/components/layout/PageHeader'
import { Pagination } from '@/components/Pagination'
import { EmptyState, ErrorState, InlineSpinner } from '@/components/states'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatMoney } from '@/lib/format'
import { hasAtMostTwoDecimals } from '@/lib/money'

type Product = components['schemas']['ProductResponse']
const PAGE_SIZE = 20
type ActiveFilter = 'active' | 'inactive' | 'all'

export function ProductsPage() {
  const [filter, setFilter] = useState<ActiveFilter>('active')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Product | 'new' | null>(null)
  const [deactivating, setDeactivating] = useState<Product | null>(null)

  const products = useQuery({
    queryKey: ['products', 'list', filter, page],
    queryFn: () => unwrap(api.GET('/api/v1/products', {
      params: { query: { active: filter === 'all' ? undefined : filter === 'active', sort: ['name,asc'], page, size: PAGE_SIZE } },
    })),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader
        title="Products"
        description="The catalog. A SKU is permanent once created; retire products instead of deleting them so order history stays intact."
        actions={<Button onClick={() => setEditing('new')}><Plus className="size-4" /> New product</Button>}
      />
      <Tabs value={filter} onValueChange={(value) => { setFilter(value as ActiveFilter); setPage(0) }} className="mb-4">
        <TabsList>
          <TabsTrigger value="active">Active</TabsTrigger>
          <TabsTrigger value="inactive">Retired</TabsTrigger>
          <TabsTrigger value="all">All</TabsTrigger>
        </TabsList>
      </Tabs>

      {products.isPending ? <InlineSpinner label="Loading products" />
        : products.isError ? <ErrorState error={products.error} onRetry={() => products.refetch()} />
        : products.data.content.length === 0 ? (
          <EmptyState title="No products here">Create one with New product.</EmptyState>
        ) : (
          <>
            <div className="rounded-xl border border-border bg-panel">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead>SKU</TableHead>
                    <TableHead className="text-right">Price</TableHead>
                    <TableHead>State</TableHead>
                    <TableHead><span className="sr-only">Actions</span></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {products.data.content.map((product) => (
                    <TableRow key={product.id}>
                      <TableCell>
                        <p>{product.name}</p>
                        {product.description !== null && product.description !== undefined && (
                          <p className="max-w-md truncate text-xs text-muted-foreground">{product.description}</p>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">{product.sku}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatMoney(product.price)}</TableCell>
                      <TableCell className={product.active ? '' : 'text-muted-foreground'}>{product.active ? 'Active' : 'Retired'}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button size="sm" variant="outline" onClick={() => setEditing(product)}>Edit</Button>
                          {product.active && <Button size="sm" variant="destructive" onClick={() => setDeactivating(product)}>Retire</Button>}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <Pagination page={products.data.page} totalPages={products.data.totalPages} totalElements={products.data.totalElements} onPageChange={setPage} />
          </>
        )}

      {editing !== null && <ProductDialog product={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      <RetireDialog product={deactivating} onClose={() => setDeactivating(null)} />
    </>
  )
}

/** Mirrors CreateProductRequest / UpdateProductRequest validation. */
const productSchema = z.object({
  sku: z.string().trim().regex(/^[A-Za-z0-9][A-Za-z0-9-]{2,63}$/, '3-64 letters, digits or hyphens, starting with a letter or digit'),
  name: z.string().trim().min(1, 'Enter a name').max(200),
  description: z.string().trim().max(2000),
  price: z.number({ error: 'Enter a price' }).min(0, 'Price cannot be negative').max(9_999_999_999.99)
    .refine(hasAtMostTwoDecimals, 'Use at most 2 decimals'),
})
type ProductValues = z.infer<typeof productSchema>
const PRODUCT_FIELDS = ['sku', 'name', 'description', 'price'] as const

function ProductDialog({ product, onClose }: { product: Product | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const creating = product === null
  const { register, handleSubmit, setError, formState: { errors } } = useForm<ProductValues>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      sku: product?.sku ?? '',
      name: product?.name ?? '',
      description: product?.description ?? '',
      price: product?.price,
    },
  })

  const save = useMutation({
    mutationFn: (values: ProductValues) => {
      const description = values.description === '' ? undefined : values.description
      return creating
        ? unwrap(api.POST('/api/v1/products', { body: { sku: values.sku, name: values.name, description, price: values.price } }))
        : unwrap(api.PUT('/api/v1/products/{id}', { params: { path: { id: product.id } }, body: { name: values.name, description, price: values.price } }))
    },
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ['products'] })
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
      void queryClient.invalidateQueries({ queryKey: ['catalog'] })
      toast.success(creating ? `${saved.name} created` : `${saved.name} saved`)
      onClose()
    },
    onError: (error) => {
      if (!applyServerFieldErrors(error, setError, PRODUCT_FIELDS)) toast.error(errorMessage(error))
    },
  })

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{creating ? 'New product' : `Edit ${product.name}`}</DialogTitle>
          <DialogDescription>
            {creating ? 'New products start with no stock. Add stock from Inventory.' : 'The SKU cannot change. Existing orders keep the price they were placed at.'}
          </DialogDescription>
        </DialogHeader>
        <form id="product-form" noValidate className="grid gap-4" onSubmit={handleSubmit((values) => save.mutate(values))}>
          <Field id="sku" label="SKU" error={errors.sku?.message}>
            <Input id="sku" disabled={!creating} autoFocus={creating} aria-invalid={errors.sku !== undefined} {...register('sku')} />
          </Field>
          <Field id="name" label="Name" error={errors.name?.message}>
            <Input id="name" autoFocus={!creating} aria-invalid={errors.name !== undefined} {...register('name')} />
          </Field>
          <Field id="description" label="Description (optional)" error={errors.description?.message}>
            <Input id="description" aria-invalid={errors.description !== undefined} {...register('description')} />
          </Field>
          <Field id="price" label="Price (USD)" error={errors.price?.message}>
            <Input id="price" type="number" step="0.01" min={0} aria-invalid={errors.price !== undefined} {...register('price', { valueAsNumber: true })} />
          </Field>
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Close</Button>
          <Button type="submit" form="product-form" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : creating ? 'Create product' : 'Save changes'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function RetireDialog({ product, onClose }: { product: Product | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const retire = useMutation({
    mutationFn: (id: number) => unwrap(api.DELETE('/api/v1/products/{id}', { params: { path: { id } } })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['products'] })
      void queryClient.invalidateQueries({ queryKey: ['catalog'] })
      toast.success(`${product?.name ?? 'Product'} retired`)
      onClose()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })
  return (
    <AlertDialog open={product !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Retire {product?.name}?</AlertDialogTitle>
          <AlertDialogDescription>
            It disappears from the shop and can no longer be ordered. Past orders and stock history keep it.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Keep it</AlertDialogCancel>
          <AlertDialogAction onClick={() => { if (product !== null) retire.mutate(product.id) }}>Retire product</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

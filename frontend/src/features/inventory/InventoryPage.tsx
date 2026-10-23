import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Search } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'

import { api, unwrap } from '@/api/client'
import { errorMessage } from '@/api/errors'
import type { components } from '@/api/schema'
import { Field } from '@/components/form'
import { applyServerFieldErrors } from '@/lib/form-errors'
import { isStaff, useCurrentUser } from '@/auth/useAuth'
import { PageHeader } from '@/components/layout/PageHeader'
import { Pagination } from '@/components/Pagination'
import { EmptyState, ErrorState, InlineSpinner } from '@/components/states'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatCount, formatDateTime, orderReference } from '@/lib/format'
import { useDebouncedValue } from '@/lib/useDebouncedValue'
import { cn } from '@/lib/utils'

type StockLevel = components['schemas']['InventoryResponse']
type MovementType = components['schemas']['StockMovementResponse']['type']
const PAGE_SIZE = 20
const MAX_ADJUSTMENT = 1_000_000

export function InventoryPage() {
  const canAdjust = isStaff(useCurrentUser())
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const query = useDebouncedValue(search.trim(), 300)
  const [adjusting, setAdjusting] = useState<StockLevel | null>(null)
  const [historyFor, setHistoryFor] = useState<StockLevel | null>(null)

  const stock = useQuery({
    queryKey: ['inventory', 'list', { query, page }],
    queryFn: () => unwrap(api.GET('/api/v1/inventory', {
      params: { query: { q: query === '' ? undefined : query.slice(0, 100), page, size: PAGE_SIZE } },
    })),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader title="Inventory" description="On hand is what is on the shelf. Reserved is promised to pending orders. Available is what can still be sold." />
      <div className="relative mb-4 sm:max-w-sm">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input
          aria-label="Search stock by name or SKU"
          placeholder="Name or SKU"
          className="pl-9"
          maxLength={100}
          value={search}
          onChange={(event) => { setSearch(event.target.value); setPage(0) }}
        />
      </div>
      {stock.isPending ? <InlineSpinner label="Loading stock" />
        : stock.isError ? <ErrorState error={stock.error} onRetry={() => stock.refetch()} />
        : stock.data.content.length === 0 && query !== '' ? (
          <EmptyState title={`Nothing matches "${query}"`}>Try a shorter name or a different SKU.</EmptyState>
        ) : stock.data.content.length === 0 ? (
          <EmptyState title="No products yet"><Link className="text-primary" to="/products">Add a product</Link>; its stock appears here automatically.</EmptyState>
        ) : (
          <>
            <div className="rounded-xl border border-border bg-panel">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead className="text-right">On hand</TableHead>
                    <TableHead className="text-right">Reserved</TableHead>
                    <TableHead className="text-right">Available</TableHead>
                    <TableHead className="text-right">Updated</TableHead>
                    <TableHead><span className="sr-only">Actions</span></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {stock.data.content.map((level) => (
                    <TableRow key={level.productId}>
                      <TableCell>
                        <p>{level.name}</p>
                        <p className="text-xs text-muted-foreground">{level.sku}</p>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{formatCount(level.onHand)}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatCount(level.reserved)}</TableCell>
                      <TableCell className={cn('text-right font-medium tabular-nums', level.available === 0 && 'text-status-cancelled')}>
                        {formatCount(level.available)}
                      </TableCell>
                      <TableCell className="text-right text-muted-foreground">{formatDateTime(level.updatedAt)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button size="sm" variant="outline" onClick={() => setHistoryFor(level)}>History</Button>
                          {canAdjust && <Button size="sm" onClick={() => setAdjusting(level)}>Adjust</Button>}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <Pagination page={stock.data.page} totalPages={stock.data.totalPages} totalElements={stock.data.totalElements} onPageChange={setPage} />
          </>
        )}

      {adjusting !== null && <AdjustDialog level={adjusting} onClose={() => setAdjusting(null)} />}
      <MovementSheet level={historyFor} onClose={() => setHistoryFor(null)} />
    </>
  )
}

const adjustSchema = z.object({
  quantityChange: z.number({ error: 'Enter a whole number' })
    .int('Enter a whole number')
    .min(-MAX_ADJUSTMENT).max(MAX_ADJUSTMENT)
    .refine((value) => value !== 0, 'A change of 0 does nothing'),
  reason: z.string().trim().min(1, 'Say why stock is changing').max(255),
})
type AdjustValues = z.infer<typeof adjustSchema>

function AdjustDialog({ level, onClose }: { level: StockLevel; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { register, handleSubmit, setError, watch, formState: { errors } } = useForm<AdjustValues>({
    resolver: zodResolver(adjustSchema),
    defaultValues: { reason: '' },
  })
  const change = watch('quantityChange')
  const preview = Number.isInteger(change) ? level.onHand + change : null

  const adjust = useMutation({
    mutationFn: (values: AdjustValues) => unwrap(api.POST('/api/v1/inventory/{productId}/adjustments', {
      params: { path: { productId: level.productId } },
      body: values,
    })),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      toast.success(`${updated.name}: ${formatCount(updated.onHand)} on hand`)
      onClose()
    },
    onError: (error) => {
      if (!applyServerFieldErrors(error, setError, ['quantityChange', 'reason'])) {
        toast.error(errorMessage(error))
      }
    },
  })

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Adjust stock: {level.name}</DialogTitle>
          <DialogDescription>
            {formatCount(level.onHand)} on hand, {formatCount(level.reserved)} reserved. Use a positive number for a delivery and a negative one for damage or a count correction.
          </DialogDescription>
        </DialogHeader>
        <form id="adjust-form" noValidate className="grid gap-4" onSubmit={handleSubmit((values) => adjust.mutate(values))}>
          <Field id="quantityChange" label="Change" error={errors.quantityChange?.message}
            hint={preview === null ? undefined : `On hand after: ${formatCount(preview)}`}>
            <Input id="quantityChange" type="number" step={1} autoFocus aria-invalid={errors.quantityChange !== undefined}
              {...register('quantityChange', { valueAsNumber: true })} />
          </Field>
          <Field id="reason" label="Reason" error={errors.reason?.message}>
            <Input id="reason" maxLength={255} placeholder="Delivery from supplier" aria-invalid={errors.reason !== undefined} {...register('reason')} />
          </Field>
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Close</Button>
          <Button type="submit" form="adjust-form" disabled={adjust.isPending}>{adjust.isPending ? 'Saving…' : 'Save adjustment'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

const MOVEMENT_LABEL: Record<MovementType, string> = {
  ADJUSTMENT: 'Adjustment',
  RESERVATION: 'Reserved',
  RELEASE: 'Released',
  FULFILLMENT: 'Fulfilled',
  RESTOCK: 'Restocked',
}

function MovementSheet({ level, onClose }: { level: StockLevel | null; onClose: () => void }) {
  const [type, setType] = useState<MovementType | 'ALL'>('ALL')
  const movements = useQuery({
    queryKey: ['inventory', 'movements', level?.productId, type],
    queryFn: () => unwrap(api.GET('/api/v1/inventory/{productId}/movements', {
      params: { path: { productId: level?.productId ?? 0 }, query: { type: type === 'ALL' ? undefined : type, page: 0, size: 50 } },
    })),
    enabled: level !== null,
  })

  return (
    <Sheet open={level !== null} onOpenChange={(open) => { if (!open) { onClose(); setType('ALL') } }}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Stock history{level !== null ? `: ${level.name}` : ''}</SheetTitle>
          <SheetDescription>Every change to this product's stock, newest first.</SheetDescription>
        </SheetHeader>
        <div className="grid gap-4 px-4 pb-6">
          <Select value={type} onValueChange={(value) => setType(value as MovementType | 'ALL')}>
            <SelectTrigger className="w-48" aria-label="Filter by movement type"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All changes</SelectItem>
              {Object.entries(MOVEMENT_LABEL).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}
            </SelectContent>
          </Select>
          {movements.isPending ? <InlineSpinner label="Loading history" />
            : movements.isError ? <ErrorState error={movements.error} onRetry={() => movements.refetch()} />
            : movements.data.content.length === 0 ? <p className="text-sm text-muted-foreground">No changes of this kind yet.</p>
            : (
              <ol className="divide-y divide-border">
                {movements.data.content.map((movement) => (
                  <li key={movement.id} className="grid grid-cols-[1fr_auto] gap-x-4 py-3">
                    <p className="font-medium">{MOVEMENT_LABEL[movement.type]}</p>
                    <p className="text-right tabular-nums">
                      {signed(movement.onHandChange)} on hand
                      {movement.reservedChange !== 0 && <span className="block text-xs text-muted-foreground">{signed(movement.reservedChange)} reserved</span>}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {movement.reason}
                      {movement.orderId !== null && movement.orderId !== undefined && (
                        <> (<Link className="text-primary" to={`/orders/${movement.orderId}`} onClick={onClose}>{orderReference(movement.orderId)}</Link>)</>
                      )}
                    </p>
                    <p className="text-right text-xs text-muted-foreground">{formatDateTime(movement.createdAt)}</p>
                  </li>
                ))}
              </ol>
            )}
        </div>
      </SheetContent>
    </Sheet>
  )
}

function signed(value: number): string {
  return value > 0 ? `+${formatCount(value)}` : formatCount(value)
}

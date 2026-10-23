import { ChevronLeft, ChevronRight } from 'lucide-react'

import { Button } from '@/components/ui/button'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
}

/** Zero-based page index, matching the backend's PageResponse. */
export function Pagination({ page, totalPages, totalElements, onPageChange }: PaginationProps) {
  if (totalPages <= 1) {
    return <p className="mt-4 text-sm text-muted-foreground">{totalElements} total</p>
  }
  return (
    <div className="mt-4 flex items-center justify-between gap-4">
      <p className="text-sm text-muted-foreground">
        Page {page + 1} of {totalPages} ({totalElements} total)
      </p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
          <ChevronLeft className="size-4" /> Previous
        </Button>
        <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
          Next <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  )
}

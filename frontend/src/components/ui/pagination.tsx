import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "./button";

export function Pagination({
  page,
  size,
  total,
  totalPages,
  onPageChange,
  noun = "items",
}: {
  /** zero-based page index */
  page: number;
  size: number;
  total: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  noun?: string;
}) {
  if (total === 0) return null;
  const from = page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return (
    <div className="flex items-center justify-between gap-3 border-t border-line px-4 py-2.5 text-xs text-fg-muted">
      <span className="tabular">
        {from.toLocaleString()}–{to.toLocaleString()} of {total.toLocaleString()} {noun}
      </span>
      <div className="flex items-center gap-1.5">
        <Button size="sm" variant="ghost" disabled={page === 0} onClick={() => onPageChange(page - 1)} aria-label="Previous page">
          <ChevronLeft className="size-3.5" /> Prev
        </Button>
        <span className="tabular px-1">
          {page + 1} / {Math.max(totalPages, 1)}
        </span>
        <Button
          size="sm"
          variant="ghost"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          aria-label="Next page"
        >
          Next <ChevronRight className="size-3.5" />
        </Button>
      </div>
    </div>
  );
}

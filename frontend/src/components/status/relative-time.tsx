"use client";

import { formatFull, timeAgo } from "@/lib/format";
import { useNow } from "@/stores/clock-store";

/** "12m ago", kept fresh by the shared clock; the exact timestamp is in the tooltip. */
export function RelativeTime({ iso, className, fallback = "—" }: { iso: string | null | undefined; className?: string; fallback?: string }) {
  const now = useNow();
  if (!iso) return <span className={className}>{fallback}</span>;
  return (
    <time dateTime={iso} title={formatFull(iso)} className={className} suppressHydrationWarning>
      {timeAgo(iso, now)}
    </time>
  );
}

import { cn } from "@/lib/cn";
import { initials } from "@/lib/format";

export function Avatar({ name, size = "md", className }: { name: string; size?: "sm" | "md"; className?: string }) {
  return (
    <span
      title={name}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full border border-line-strong bg-surface-3 font-semibold text-fg-muted",
        size === "sm" ? "size-5 text-[9px]" : "size-7 text-[11px]",
        className,
      )}
      aria-hidden
    >
      {initials(name)}
    </span>
  );
}

export function UserLabel({ name, className }: { name: string | null | undefined; className?: string }) {
  if (!name) return <span className={cn("text-fg-subtle", className)}>Unassigned</span>;
  return (
    <span className={cn("inline-flex min-w-0 items-center gap-2", className)}>
      <Avatar name={name} size="sm" />
      <span className="truncate">{name}</span>
    </span>
  );
}

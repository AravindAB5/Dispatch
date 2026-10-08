import { cn } from "@/lib/cn";

/** Dispatch mark: a signal pulse inside a rounded square. */
export function DispatchMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={cn("size-7", className)} aria-hidden>
      <rect x="1" y="1" width="30" height="30" rx="8" fill="#8b96ff" />
      <path
        d="M5.5 17h5l2.5-6 4 11 3-8 1.5 3h5"
        fill="none"
        stroke="#0d0f2a"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function DispatchLogo({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <DispatchMark />
      <span className="text-[15px] font-semibold tracking-tight text-fg">Dispatch</span>
    </span>
  );
}

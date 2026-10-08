"use client";

import { CircleCheck, Info, OctagonX, TriangleAlert, X } from "lucide-react";
import Link from "next/link";

import { cn } from "@/lib/cn";
import { useToastStore, type ToastTone } from "@/stores/toast-store";

const tones: Record<ToastTone, { Icon: typeof Info; className: string; bar: string }> = {
  info: { Icon: Info, className: "text-accent", bar: "bg-accent" },
  success: { Icon: CircleCheck, className: "text-ok", bar: "bg-ok" },
  warning: { Icon: TriangleAlert, className: "text-sev-high", bar: "bg-sev-high" },
  critical: { Icon: OctagonX, className: "text-sev-critical", bar: "bg-sev-critical" },
  error: { Icon: TriangleAlert, className: "text-bad", bar: "bg-bad" },
};

/** Bottom-right notification stack. Live incidents use it sparingly (HIGH/CRITICAL only). */
export function Toaster() {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);

  return (
    <div
      aria-live="polite"
      aria-relevant="additions"
      className="pointer-events-none fixed inset-x-3 bottom-3 z-[60] flex flex-col items-end gap-2 sm:inset-x-auto sm:right-4 sm:bottom-4"
    >
      {toasts.map((toast) => {
        const { Icon, className, bar } = tones[toast.tone];
        const body = (
          <div className="flex min-w-0 flex-1 items-start gap-3">
            <Icon className={cn("mt-0.5 size-4 shrink-0", className)} aria-hidden />
            <div className="min-w-0">
              <p className="text-sm font-medium text-fg">{toast.title}</p>
              {toast.description && <p className="mt-0.5 line-clamp-2 text-xs text-fg-muted">{toast.description}</p>}
            </div>
          </div>
        );
        return (
          <div
            key={toast.id}
            role={toast.tone === "error" || toast.tone === "critical" ? "alert" : "status"}
            className="pointer-events-auto relative flex w-full max-w-sm animate-slide-in items-start gap-2 overflow-hidden rounded-lg border border-line-strong bg-surface-2/95 py-3 pl-4 pr-2 shadow-xl shadow-black/40 backdrop-blur"
          >
            <span className={cn("absolute inset-y-0 left-0 w-0.5", bar)} aria-hidden />
            {toast.href ? (
              <Link href={toast.href} className="flex min-w-0 flex-1 hover:opacity-90" onClick={() => dismiss(toast.id)}>
                {body}
              </Link>
            ) : (
              body
            )}
            <button
              type="button"
              aria-label="Dismiss notification"
              onClick={() => dismiss(toast.id)}
              className="rounded p-1 text-fg-subtle hover:bg-surface-3 hover:text-fg"
            >
              <X className="size-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}

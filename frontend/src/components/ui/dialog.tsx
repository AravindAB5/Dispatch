"use client";

import { X } from "lucide-react";
import { useEffect, useId, useRef, type ReactNode } from "react";

import { cn } from "@/lib/cn";

import { IconButton } from "./button";

/**
 * Modal built on the native <dialog> element: focus trapping, Escape to close and inert
 * background come from the browser rather than a library.
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  variant = "modal",
  side = "right",
  size = "md",
  className,
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  variant?: "modal" | "drawer";
  side?: "left" | "right";
  size?: "md" | "lg";
  className?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  // Only a press that both starts and ends on the backdrop closes the dialog, so drag-selecting text
  // in a field and releasing outside the panel doesn't throw away what was typed.
  const pressStartedOnBackdrop = useRef(false);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
      // showModal() focuses the first focusable element (the close button); prefer the first field.
      dialog
        .querySelector<HTMLElement>(
          "[data-dialog-body] :is(input:not([disabled]):not([type=hidden]), textarea:not([disabled]), select:not([disabled]))",
        )
        ?.focus();
    }
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onPointerDown={(event) => {
        // A press on the backdrop lands on the <dialog> element itself.
        pressStartedOnBackdrop.current = event.target === ref.current;
      }}
      onClick={(event) => {
        if (event.target === ref.current && pressStartedOnBackdrop.current) onClose();
        pressStartedOnBackdrop.current = false;
      }}
      className={cn(
        "border border-line-strong bg-surface p-0 text-fg shadow-2xl shadow-black/50 outline-none",
        variant === "modal" && "m-auto max-h-[90dvh] rounded-xl",
        variant === "modal" && (size === "lg" ? "w-[min(94vw,42rem)]" : "w-[min(94vw,34rem)]"),
        variant === "drawer" && "my-0 h-dvh max-h-none rounded-none border-y-0",
        variant === "drawer" && side === "right" && "ml-auto mr-0 w-[min(100vw,32rem)] border-r-0",
        variant === "drawer" && side === "left" && "ml-0 mr-auto w-72 border-l-0",
        className,
      )}
    >
      {open && (
        <div className={cn("flex flex-col", variant === "drawer" ? "h-full" : "max-h-[90dvh]")}>
          <header className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
            <div className="min-w-0">
              <h2 id={titleId} className="text-base font-semibold text-fg">
                {title}
              </h2>
              {description && <p className="mt-0.5 text-sm text-fg-muted">{description}</p>}
            </div>
            <IconButton label="Close" onClick={onClose} className="-mr-2 -mt-1">
              <X className="size-4" />
            </IconButton>
          </header>
          <div data-dialog-body className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
            {children}
          </div>
          {footer && <footer className="flex justify-end gap-2 border-t border-line px-5 py-3">{footer}</footer>}
        </div>
      )}
    </dialog>
  );
}

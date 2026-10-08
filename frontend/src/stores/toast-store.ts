import { create } from "zustand";

export type ToastTone = "info" | "success" | "error" | "critical" | "warning";

export type Toast = {
  id: number;
  tone: ToastTone;
  title: string;
  description?: string;
  href?: string;
  /** milliseconds; 0 keeps the toast until dismissed */
  duration: number;
};

type ToastState = {
  toasts: Toast[];
  push: (toast: Omit<Toast, "id" | "duration"> & { duration?: number }) => void;
  dismiss: (id: number) => void;
};

const MAX_TOASTS = 4;
let nextId = 1;

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (toast) => {
    const id = nextId++;
    const duration = toast.duration ?? (toast.tone === "error" ? 8000 : 6000);
    set((state) => ({ toasts: [...state.toasts, { ...toast, id, duration }].slice(-MAX_TOASTS) }));
    if (duration > 0) {
      setTimeout(() => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })), duration);
    }
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));

/** Imperative helper usable outside React components (mutation callbacks, socket handlers). */
export const toast = {
  success: (title: string, description?: string) => useToastStore.getState().push({ tone: "success", title, description }),
  error: (title: string, description?: string) => useToastStore.getState().push({ tone: "error", title, description }),
  info: (title: string, description?: string) => useToastStore.getState().push({ tone: "info", title, description }),
  show: (toast: Omit<Toast, "id" | "duration"> & { duration?: number }) => useToastStore.getState().push(toast),
};

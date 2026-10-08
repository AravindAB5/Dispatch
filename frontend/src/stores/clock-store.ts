import { useEffect } from "react";
import { create } from "zustand";

/**
 * One shared clock for every "12m ago" label on the page, instead of a timer per component.
 */
export const useClockStore = create<{ now: number; tick: () => void }>((set) => ({
  now: Date.now(),
  tick: () => set({ now: Date.now() }),
}));

export function useNow(): number {
  return useClockStore((state) => state.now);
}

/** Mount once near the root. */
export function useClockTicker(intervalMs = 10_000) {
  useEffect(() => {
    const tick = useClockStore.getState().tick;
    tick();
    const timer = setInterval(tick, intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
}

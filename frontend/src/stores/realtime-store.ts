import { create } from "zustand";

import type { DispatchEvent } from "@/lib/api/types";

export type ConnectionStatus = "connecting" | "live" | "reconnecting" | "offline";

export const LIVE_EVENT_BUFFER = 150;
const HIGHLIGHT_MS = 4000;

type RealtimeState = {
  status: ConnectionStatus;
  lastMessageAt: number | null;
  /** Most recent events pushed over the socket, newest first (bounded). */
  liveEvents: DispatchEvent[];
  /** Entity keys (e.g. "incident:41") → time until which the UI should highlight them. */
  highlights: Record<string, number>;
  setStatus: (status: ConnectionStatus) => void;
  receivedMessage: () => void;
  pushEvent: (event: DispatchEvent) => void;
  highlight: (key: string) => void;
  reset: () => void;
};

/**
 * Global client state for real-time behaviour. Server data lives in TanStack Query; this store only
 * holds what the socket adds on top: connection health, the live event buffer and transient highlights.
 */
export const useRealtimeStore = create<RealtimeState>((set) => ({
  status: "connecting",
  lastMessageAt: null,
  liveEvents: [],
  highlights: {},
  setStatus: (status) => set({ status }),
  receivedMessage: () => set({ lastMessageAt: Date.now() }),
  pushEvent: (event) =>
    set((state) =>
      state.liveEvents.some((e) => e.id === event.id)
        ? state
        : { liveEvents: [event, ...state.liveEvents].slice(0, LIVE_EVENT_BUFFER) },
    ),
  highlight: (key) =>
    set((state) => {
      const now = Date.now();
      const highlights = Object.fromEntries(Object.entries(state.highlights).filter(([, until]) => until > now));
      highlights[key] = now + HIGHLIGHT_MS;
      return { highlights };
    }),
  reset: () => set({ status: "connecting", lastMessageAt: null, liveEvents: [], highlights: {} }),
}));

/**
 * The highlight stamp for an entity changed over the socket (undefined if never highlighted).
 * Use it in a React `key` so the flash animation replays on every change.
 */
export function useHighlightStamp(key: string): number | undefined {
  return useRealtimeStore((state) => state.highlights[key]);
}

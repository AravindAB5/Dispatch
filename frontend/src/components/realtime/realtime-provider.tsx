"use client";

import { Client } from "@stomp/stompjs";
import { useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useEffect, type ReactNode } from "react";

import { authApi } from "@/lib/api/endpoints";
import type { RealtimeMessage } from "@/lib/api/types";
import { getToken } from "@/lib/auth/session";
import { WS_URL } from "@/lib/config";
import { handleRealtimeMessage } from "@/lib/realtime/handle-message";
import { useRealtimeStore } from "@/stores/realtime-store";
import { useToastStore } from "@/stores/toast-store";

const TOPICS = ["/topic/events", "/topic/incidents", "/topic/services", "/topic/devices"];

/**
 * Owns the single STOMP-over-WebSocket connection for the signed-in console. Reconnects
 * automatically; after a reconnect every query is refreshed, because messages sent while the
 * socket was down are not replayed.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  useEffect(() => {
    const token = getToken();
    if (!token) return;

    const store = useRealtimeStore.getState();
    const pending = new Map<string, ReturnType<typeof setTimeout>>();
    const invalidate = (key: QueryKey, delayMs: number) => {
      const id = JSON.stringify(key);
      if (pending.has(id)) return; // coalesce bursts into one refetch
      pending.set(
        id,
        setTimeout(() => {
          pending.delete(id);
          void queryClient.invalidateQueries({ queryKey: key });
        }, delayMs),
      );
    };
    const effects = {
      invalidate,
      pushEvent: store.pushEvent,
      highlight: store.highlight,
      notify: useToastStore.getState().push,
    };

    let connectedOnce = false;
    store.setStatus("connecting");

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        const isReconnect = connectedOnce;
        connectedOnce = true;
        useRealtimeStore.getState().setStatus("live");
        for (const topic of TOPICS) {
          client.subscribe(topic, (frame) => {
            try {
              const message = JSON.parse(frame.body) as RealtimeMessage;
              useRealtimeStore.getState().receivedMessage();
              handleRealtimeMessage(message, effects);
            } catch (error) {
              console.warn("Ignoring malformed realtime message", error);
            }
          });
        }
        if (isReconnect) void queryClient.invalidateQueries();
      },
      onWebSocketClose: () => {
        useRealtimeStore.getState().setStatus(connectedOnce ? "reconnecting" : "connecting");
      },
      onStompError: () => {
        // Most likely an expired token: ask the API, which redirects to login on 401.
        useRealtimeStore.getState().setStatus("offline");
        void authApi.me().catch(() => undefined);
      },
    });
    client.activate();

    return () => {
      pending.forEach((timer) => clearTimeout(timer));
      void client.deactivate();
      useRealtimeStore.getState().reset();
    };
  }, [queryClient]);

  return children;
}

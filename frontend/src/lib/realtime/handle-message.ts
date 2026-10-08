import type { QueryKey } from "@tanstack/react-query";

import type { RealtimeMessage } from "@/lib/api/types";
import { SEVERITY_LABEL } from "@/lib/domain";
import { qk } from "@/lib/query-keys";
import type { Toast } from "@/stores/toast-store";

export type RealtimeEffects = {
  /** Invalidate a query family, coalescing bursts within `delayMs`. */
  invalidate: (key: QueryKey, delayMs: number) => void;
  pushEvent: (event: Extract<RealtimeMessage, { type: "event.created" }>["data"]) => void;
  highlight: (key: string) => void;
  notify: (toast: Omit<Toast, "id" | "duration"> & { duration?: number }) => void;
};

/**
 * Translates one WebSocket message into UI effects. Lists and aggregates are refetched
 * (invalidated) rather than patched by hand, so the server stays the single source of truth;
 * the socket only tells the client *when* and *what* to refresh.
 */
export function handleRealtimeMessage(message: RealtimeMessage, fx: RealtimeEffects): void {
  switch (message.type) {
    case "event.created": {
      fx.pushEvent(message.data);
      fx.invalidate(qk.dashboard.summary, 1500);
      fx.invalidate(["dashboard", "volume"], 5000);
      for (const link of message.data.incidents) {
        fx.invalidate(qk.incident.events(link.id), 500);
      }
      if (message.data.device) fx.invalidate(qk.devices.detail(message.data.device.id), 2000);
      return;
    }
    case "incident.created":
    case "incident.updated":
    case "incident.resolved": {
      const incident = message.data;
      fx.highlight(`incident:${incident.id}`);
      fx.invalidate(qk.incidents.all, 400);
      fx.invalidate(qk.incident.all(incident.id), 250);
      fx.invalidate(qk.dashboard.all, 1000);
      fx.invalidate(qk.services.all, 1000);
      fx.invalidate(qk.rules, 3000);
      fx.invalidate(qk.users, 3000);
      if (message.type === "incident.created" && (incident.severity === "CRITICAL" || incident.severity === "HIGH")) {
        fx.notify({
          tone: incident.severity === "CRITICAL" ? "critical" : "warning",
          title: `${SEVERITY_LABEL[incident.severity]} incident opened`,
          description: `${incident.key} · ${incident.title}`,
          href: `/incidents/${incident.id}`,
        });
      }
      return;
    }
    case "service.updated": {
      const service = message.data;
      fx.highlight(`service:${service.id}`);
      fx.invalidate(qk.services.all, 300);
      fx.invalidate(qk.devices.all, 1000); // device pages show their service's health
      fx.invalidate(qk.dashboard.summary, 1000);
      if (service.status === "DOWN") {
        fx.notify({ tone: "critical", title: `${service.name} is down`, href: `/services/${service.id}` });
      } else if (service.previousStatus === "DOWN") {
        fx.notify({ tone: "success", title: `${service.name} recovered`, description: `Now ${service.status.toLowerCase()}` });
      }
      return;
    }
    case "device.updated": {
      fx.highlight(`device:${message.data.id}`);
      fx.invalidate(qk.devices.all, 600);
      // Service cards and the health panel show "x/y devices up", even when service health is unchanged.
      fx.invalidate(qk.services.all, 1000);
      fx.invalidate(qk.dashboard.summary, 1000);
      return;
    }
  }
}

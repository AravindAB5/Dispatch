import { CircleCheck, CircleDot, OctagonX, Search, TriangleAlert, Undo2, WifiOff } from "lucide-react";

import type { DeviceStatus, EventSeverity, IncidentStatus, ServiceStatus } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { DEVICE_STATUS_LABEL, SERVICE_STATUS_LABEL, SEVERITY_LABEL, STATUS_LABEL } from "@/lib/domain";

/*
 * Visual language:
 *  - Severity = tinted, uppercase block with a solid marker. Loudest element in a row.
 *  - Incident status = neutral pill with a coloured dot. Distinct shape from severity.
 *  - Health = icon + label (never colour alone).
 */

const severityStyles: Record<EventSeverity, string> = {
  CRITICAL: "text-sev-critical bg-sev-critical/12 ring-sev-critical/30",
  HIGH: "text-sev-high bg-sev-high/12 ring-sev-high/30",
  MEDIUM: "text-sev-medium bg-sev-medium/10 ring-sev-medium/25",
  LOW: "text-sev-low bg-sev-low/10 ring-sev-low/25",
  INFO: "text-fg-subtle bg-surface-3 ring-line-strong",
};

export function SeverityBadge({
  severity,
  size = "md",
  className,
}: {
  severity: EventSeverity;
  size?: "sm" | "md";
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded font-semibold uppercase tracking-wide ring-1 ring-inset",
        size === "md" ? "h-5 px-1.5 text-[10.5px]" : "h-[18px] px-1 text-[9.5px]",
        severityStyles[severity],
        className,
      )}
    >
      <span className={cn("rounded-[1px] bg-current", size === "md" ? "size-1.5" : "size-1")} aria-hidden />
      {SEVERITY_LABEL[severity]}
    </span>
  );
}

const statusDot: Record<IncidentStatus, string> = {
  OPEN: "bg-st-open text-st-open animate-pulse-ring",
  INVESTIGATING: "bg-st-investigating",
  RESOLVED: "bg-st-resolved",
};

export function StatusBadge({ status, className }: { status: IncidentStatus; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex h-6 shrink-0 items-center gap-1.5 rounded-full border border-line-strong bg-surface-2 px-2.5 text-xs font-medium text-fg",
        className,
      )}
    >
      <span className={cn("size-1.5 rounded-full", statusDot[status])} aria-hidden />
      {STATUS_LABEL[status]}
    </span>
  );
}

export function RecoveredChip({ className }: { className?: string }) {
  return (
    <span
      title="The source reported recovery, but the incident was left open for an operator to confirm."
      className={cn(
        "inline-flex h-6 items-center gap-1 rounded-full border border-ok/30 bg-ok/10 px-2 text-xs font-medium text-ok",
        className,
      )}
    >
      <Undo2 className="size-3" aria-hidden />
      Recovered
    </span>
  );
}

const healthStyles: Record<ServiceStatus, { className: string; Icon: typeof CircleCheck }> = {
  HEALTHY: { className: "text-ok", Icon: CircleCheck },
  DEGRADED: { className: "text-warn", Icon: TriangleAlert },
  DOWN: { className: "text-bad", Icon: OctagonX },
};

export function HealthBadge({ status, className }: { status: ServiceStatus; className?: string }) {
  const { className: tone, Icon } = healthStyles[status];
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-xs font-medium", tone, className)}>
      <Icon className="size-3.5" aria-hidden />
      {SERVICE_STATUS_LABEL[status]}
    </span>
  );
}

const deviceStyles: Record<DeviceStatus, { className: string; Icon: typeof CircleCheck }> = {
  ONLINE: { className: "text-ok", Icon: CircleDot },
  DEGRADED: { className: "text-warn", Icon: TriangleAlert },
  OFFLINE: { className: "text-bad", Icon: WifiOff },
};

export function DeviceStatusBadge({ status, className }: { status: DeviceStatus; className?: string }) {
  const { className: tone, Icon } = deviceStyles[status];
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-xs font-medium", tone, className)}>
      <Icon className="size-3.5" aria-hidden />
      {DEVICE_STATUS_LABEL[status]}
    </span>
  );
}

export function Chip({ children, className, mono }: { children: React.ReactNode; className?: string; mono?: boolean }) {
  return (
    <span
      className={cn(
        "inline-flex h-5 items-center rounded border border-line-strong bg-surface-2 px-1.5 text-[11px] text-fg-muted",
        mono && "font-mono",
        className,
      )}
    >
      {children}
    </span>
  );
}

export function SearchIcon(props: { className?: string }) {
  return <Search {...props} aria-hidden />;
}

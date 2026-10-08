import type { Service } from "@/lib/api/types";

/** Proportion of a service's devices by state. Labelled, so colour is never the only cue. */
export function DeviceMix({ devices }: { devices: Service["devices"] }) {
  if (devices.total === 0) return <p className="text-xs text-fg-subtle">No devices registered</p>;
  const segments = [
    { key: "online", value: devices.online, className: "bg-ok" },
    { key: "degraded", value: devices.degraded, className: "bg-warn" },
    { key: "offline", value: devices.offline, className: "bg-bad" },
  ].filter((s) => s.value > 0);
  return (
    <div>
      <div className="flex h-1.5 gap-0.5 overflow-hidden rounded-full" aria-hidden>
        {segments.map((s) => (
          <span key={s.key} className={s.className} style={{ flexGrow: s.value }} />
        ))}
      </div>
      <p className="tabular mt-1.5 text-xs text-fg-muted">
        {devices.online} online
        {devices.degraded > 0 && <> · {devices.degraded} degraded</>}
        {devices.offline > 0 && <span className="text-bad"> · {devices.offline} offline</span>}
        <span className="text-fg-subtle"> of {devices.total}</span>
      </p>
    </div>
  );
}

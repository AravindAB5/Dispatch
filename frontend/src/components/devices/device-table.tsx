"use client";

import { Pencil } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { DeviceStatusBadge } from "@/components/status/badges";
import { RelativeTime } from "@/components/status/relative-time";
import { IconButton } from "@/components/ui/button";
import type { Device } from "@/lib/api/types";
import { useFlash } from "@/hooks/use-flash";

function DeviceRow({ device, onEdit, showService }: { device: Device; onEdit?: (d: Device) => void; showService: boolean }) {
  const router = useRouter();
  const ref = useFlash<HTMLTableRowElement>(`device:${device.id}`);
  return (
    <tr
      ref={ref}
      onClick={(e) => {
        if (!(e.target as HTMLElement).closest("a,button")) router.push(`/devices/${device.id}`);
      }}
      className="cursor-pointer transition-colors hover:bg-surface-2"
    >
      <td className="py-2.5 pl-4 pr-3">
        <Link href={`/devices/${device.id}`} className="font-mono text-[13px] text-fg hover:text-accent-strong">
          {device.identifier}
        </Link>
      </td>
      <td className="max-w-0 py-2.5 pr-3">
        <p className="truncate text-sm text-fg">{device.name}</p>
        <p className="truncate text-xs text-fg-subtle">{device.deviceType}</p>
      </td>
      {showService && (
        <td className="hidden max-w-0 py-2.5 pr-3 text-sm md:table-cell">
          <Link href={`/services/${device.service.id}`} className="block truncate text-fg-muted hover:text-fg">
            {device.service.name}
          </Link>
        </td>
      )}
      <td className="hidden max-w-0 truncate py-2.5 pr-3 text-sm text-fg-muted lg:table-cell">{device.location ?? "—"}</td>
      <td className="py-2.5 pr-3">
        <DeviceStatusBadge status={device.status} />
      </td>
      <td className="hidden py-2.5 pr-3 text-sm text-fg-muted sm:table-cell">
        <RelativeTime iso={device.lastSeenAt} fallback="Never" />
      </td>
      <td className="tabular hidden py-2.5 pr-3 text-right text-sm xl:table-cell">
        {device.activeIncidents > 0 ? (
          <Link href={`/incidents?q=${device.identifier}`} className="text-fg hover:text-accent-strong">
            {device.activeIncidents}
          </Link>
        ) : (
          <span className="text-fg-subtle">0</span>
        )}
      </td>
      <td className="w-10 py-2.5 pr-3 text-right">
        {onEdit && (
          <IconButton label={`Edit ${device.identifier}`} onClick={() => onEdit(device)}>
            <Pencil className="size-3.5" />
          </IconButton>
        )}
      </td>
    </tr>
  );
}

export function DeviceTable({ devices, onEdit, showService = true }: { devices: Device[]; onEdit?: (d: Device) => void; showService?: boolean }) {
  return (
    <table className="w-full table-fixed">
      <colgroup>
        <col className="w-[120px]" />
        <col />
        {showService && <col className="hidden w-[190px] md:table-column" />}
        <col className="hidden w-[180px] lg:table-column" />
        <col className="w-[104px]" />
        <col className="hidden w-[100px] sm:table-column" />
        <col className="hidden w-[80px] xl:table-column" />
        <col className="w-10" />
      </colgroup>
      <thead>
        <tr className="border-b border-line text-left text-[11px] uppercase tracking-wide text-fg-subtle">
          <th className="py-2 pl-4 pr-3 font-medium">Identifier</th>
          <th className="py-2 pr-3 font-medium">Device</th>
          {showService && <th className="hidden py-2 pr-3 font-medium md:table-cell">Service</th>}
          <th className="hidden py-2 pr-3 font-medium lg:table-cell">Location</th>
          <th className="py-2 pr-3 font-medium">Status</th>
          <th className="hidden py-2 pr-3 font-medium sm:table-cell">Last seen</th>
          <th className="hidden py-2 pr-3 text-right font-medium xl:table-cell">Incidents</th>
          <th className="py-2 pr-3" aria-label="Actions" />
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {devices.map((device) => (
          <DeviceRow key={device.id} device={device} onEdit={onEdit} showService={showService} />
        ))}
      </tbody>
    </table>
  );
}

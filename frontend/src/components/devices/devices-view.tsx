"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Cpu, Plus } from "lucide-react";
import { useState } from "react";

import { SearchIcon } from "@/components/status/badges";
import { Button } from "@/components/ui/button";
import { Input, SegmentedControl, Select } from "@/components/ui/form";
import { Pagination } from "@/components/ui/pagination";
import { PageHeader } from "@/components/ui/panel";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/states";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useIsAdmin } from "@/hooks/use-me";
import { devicesApi, servicesApi, type DeviceQuery } from "@/lib/api/endpoints";
import type { Device, DeviceStatus } from "@/lib/api/types";
import { qk } from "@/lib/query-keys";

import { DeviceDialog } from "./device-dialog";
import { DeviceTable } from "./device-table";

const PAGE_SIZE = 25;

export function DevicesView() {
  const isAdmin = useIsAdmin();
  const [search, setSearch] = useState("");
  const q = useDebouncedValue(search.trim(), 300);
  const [serviceId, setServiceId] = useState("");
  const [status, setStatus] = useState<"ALL" | DeviceStatus>("ALL");
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<{ open: boolean; device?: Device }>({ open: false });

  const query: DeviceQuery = {
    q: q || undefined,
    serviceId: serviceId ? Number(serviceId) : undefined,
    status: status === "ALL" ? undefined : [status],
    page,
    size: PAGE_SIZE,
  };
  const devices = useQuery({
    queryKey: qk.devices.list(query),
    queryFn: ({ signal }) => devicesApi.list(query, signal),
    placeholderData: keepPreviousData,
  });
  const services = useQuery({ queryKey: qk.services.all, queryFn: servicesApi.list });

  return (
    <div>
      <PageHeader
        title="Devices"
        description="Endpoints that report telemetry. Offline devices are flagged the moment they stop responding."
        actions={
          isAdmin && (
            <Button variant="primary" icon={<Plus className="size-4" />} onClick={() => setDialog({ open: true })}>
              Register device
            </Button>
          )
        }
      />
      <div className="rounded-lg border border-line bg-surface">
        <div className="flex flex-col gap-2 border-b border-line p-3 md:flex-row md:items-center">
          <div className="relative flex-1 md:max-w-xs">
            <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-subtle" />
            <Input
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              placeholder="Identifier, name, type, location"
              className="pl-9"
              aria-label="Search devices"
            />
          </div>
          <Select
            aria-label="Service"
            value={serviceId}
            onChange={(e) => {
              setServiceId(e.target.value);
              setPage(0);
            }}
            className="md:w-52"
          >
            <option value="">All services</option>
            {services.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
          <SegmentedControl
            label="Status"
            value={status}
            onChange={(value) => {
              setStatus(value);
              setPage(0);
            }}
            options={[
              { value: "ALL", label: "All" },
              { value: "ONLINE", label: "Online" },
              { value: "DEGRADED", label: "Degraded" },
              { value: "OFFLINE", label: "Offline" },
            ]}
          />
        </div>
        <div style={{ opacity: devices.isPlaceholderData ? 0.6 : 1, transition: "opacity 150ms" }}>
          {devices.isPending ? (
            <SkeletonRows rows={10} />
          ) : devices.error ? (
            <ErrorState error={devices.error} title="Couldn't load devices" onRetry={() => devices.refetch()} />
          ) : devices.data.content.length === 0 ? (
            <EmptyState icon={<Cpu className="size-5" />} title="No devices match" description="Try a different search or status." />
          ) : (
            <>
              <DeviceTable devices={devices.data.content} onEdit={isAdmin ? (device) => setDialog({ open: true, device }) : undefined} />
              <Pagination
                page={devices.data.page}
                size={devices.data.size}
                total={devices.data.totalElements}
                totalPages={devices.data.totalPages}
                onPageChange={setPage}
                noun="devices"
              />
            </>
          )}
        </div>
      </div>
      {dialog.open && <DeviceDialog open onClose={() => setDialog({ open: false })} device={dialog.device} />}
    </div>
  );
}

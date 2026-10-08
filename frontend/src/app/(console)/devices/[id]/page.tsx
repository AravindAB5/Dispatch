import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { DeviceDetailView } from "@/components/devices/device-detail-view";

export const metadata: Metadata = { title: "Device" };

export default async function DevicePage({ params }: PageProps<"/devices/[id]">) {
  const { id } = await params;
  const deviceId = Number(id);
  if (!Number.isInteger(deviceId) || deviceId <= 0) notFound();
  return <DeviceDetailView id={deviceId} />;
}

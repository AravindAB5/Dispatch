import type { Metadata } from "next";

import { DevicesView } from "@/components/devices/devices-view";

export const metadata: Metadata = { title: "Devices" };

export default function DevicesPage() {
  return <DevicesView />;
}

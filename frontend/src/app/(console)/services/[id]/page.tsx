import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { ServiceDetailView } from "@/components/services/service-detail-view";

export const metadata: Metadata = { title: "Service" };

export default async function ServicePage({ params }: PageProps<"/services/[id]">) {
  const { id } = await params;
  const serviceId = Number(id);
  if (!Number.isInteger(serviceId) || serviceId <= 0) notFound();
  return <ServiceDetailView id={serviceId} />;
}

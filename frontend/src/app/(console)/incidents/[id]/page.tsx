import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { IncidentDetailView } from "@/components/incidents/incident-detail-view";

export async function generateMetadata({ params }: PageProps<"/incidents/[id]">): Promise<Metadata> {
  const { id } = await params;
  return { title: `INC-${id}` };
}

export default async function IncidentPage({ params }: PageProps<"/incidents/[id]">) {
  const { id } = await params;
  const incidentId = Number(id);
  if (!Number.isInteger(incidentId) || incidentId <= 0) notFound();
  return <IncidentDetailView id={incidentId} />;
}

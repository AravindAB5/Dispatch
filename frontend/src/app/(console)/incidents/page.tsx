import type { Metadata } from "next";
import { Suspense } from "react";

import { IncidentsView } from "@/components/incidents/incidents-view";
import { SkeletonRows } from "@/components/ui/states";

export const metadata: Metadata = { title: "Incidents" };

export default function IncidentsPage() {
  return (
    <Suspense fallback={<SkeletonRows rows={10} className="rounded-lg border border-line bg-surface" />}>
      <IncidentsView />
    </Suspense>
  );
}

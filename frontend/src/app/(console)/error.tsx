"use client";

import { useEffect } from "react";

import { Panel } from "@/components/ui/panel";
import { ErrorState } from "@/components/ui/states";

/** Catches render errors inside the console so one broken view doesn't take down the shell. */
export default function ConsoleError({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <Panel>
      <ErrorState error={error} title="This view failed to render" onRetry={() => retry()} />
    </Panel>
  );
}

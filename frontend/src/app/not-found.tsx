import { Compass } from "lucide-react";

import { DispatchLogo } from "@/components/shell/logo";
import { ButtonLink } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-6 px-4 text-center">
      <DispatchLogo />
      <div>
        <div className="mx-auto mb-4 flex size-12 items-center justify-center rounded-full bg-surface-3 text-fg-muted">
          <Compass className="size-6" aria-hidden />
        </div>
        <h1 className="text-xl font-semibold text-fg">Page not found</h1>
        <p className="mt-2 text-sm text-fg-muted">The page you were looking for doesn&apos;t exist or has moved.</p>
      </div>
      <ButtonLink href="/dashboard" variant="primary">
        Go to the dashboard
      </ButtonLink>
    </main>
  );
}

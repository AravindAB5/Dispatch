import type { ReactNode } from "react";

import { ConsoleShell } from "@/components/shell/console-shell";

/** Everything behind sign-in. `src/proxy.ts` redirects signed-out visitors before this renders. */
export default function ConsoleLayout({ children }: { children: ReactNode }) {
  return <ConsoleShell>{children}</ConsoleShell>;
}

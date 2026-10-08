"use client";

import { Menu } from "lucide-react";
import { useState, type ReactNode } from "react";

import { RealtimeProvider } from "@/components/realtime/realtime-provider";
import { Dialog } from "@/components/ui/dialog";

import { ConnectionIndicator } from "./connection-indicator";
import { DispatchLogo } from "./logo";
import { NavContent } from "./nav";
import { SimulatorControl } from "./simulator-control";

export function ConsoleShell({ children }: { children: ReactNode }) {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <RealtimeProvider>
      <div className="min-h-dvh">
        <aside className="fixed inset-y-0 left-0 z-30 hidden w-56 border-r border-line bg-[#0e1013] lg:block">
          <NavContent />
        </aside>

        <div className="lg:pl-56">
          <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-line bg-canvas/85 px-4 backdrop-blur sm:px-6 lg:px-8">
            <button
              type="button"
              onClick={() => setMenuOpen(true)}
              aria-label="Open navigation"
              className="-ml-1.5 cursor-pointer rounded-md p-1.5 text-fg-muted hover:bg-surface-3 hover:text-fg lg:hidden"
            >
              <Menu className="size-5" />
            </button>
            <DispatchLogo className="lg:hidden" />
            <div className="ml-auto flex items-center gap-2">
              <SimulatorControl />
              <ConnectionIndicator />
            </div>
          </header>
          <main className="mx-auto w-full max-w-[1480px] px-4 py-6 sm:px-6 lg:px-8">{children}</main>
        </div>

        <Dialog
          open={menuOpen}
          onClose={() => setMenuOpen(false)}
          title="Navigation"
          variant="drawer"
          side="left"
        >
          <div className="-mx-5 -my-4 h-[calc(100dvh-4.5rem)]">
            <NavContent onNavigate={() => setMenuOpen(false)} />
          </div>
        </Dialog>
      </div>
    </RealtimeProvider>
  );
}

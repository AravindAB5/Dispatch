"use client";

import { useQuery } from "@tanstack/react-query";
import { Activity, Boxes, Cpu, LayoutDashboard, LogOut, Siren, Users, Workflow } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Avatar } from "@/components/status/avatar";
import { useMe } from "@/hooks/use-me";
import { dashboardApi } from "@/lib/api/endpoints";
import { clearSession } from "@/lib/auth/session";
import { cn } from "@/lib/cn";
import { qk } from "@/lib/query-keys";

import { DispatchLogo } from "./logo";

const NAV = [
  { href: "/dashboard", label: "Dashboard", Icon: LayoutDashboard },
  { href: "/incidents", label: "Incidents", Icon: Siren, badge: "incidents" as const },
  { href: "/events", label: "Events", Icon: Activity },
  { href: "/services", label: "Services", Icon: Boxes },
  { href: "/devices", label: "Devices", Icon: Cpu },
  { href: "/rules", label: "Rules", Icon: Workflow },
  { href: "/team", label: "Team", Icon: Users },
];

export function signOut() {
  clearSession();
  // A full navigation (not router.push) deliberately discards every cached query and closes the socket.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign("/login");
}

/** Navigation + identity. Rendered as the desktop sidebar and inside the mobile drawer. */
export function NavContent({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const { data: me } = useMe();
  const { data: summary } = useQuery({ queryKey: qk.dashboard.summary, queryFn: dashboardApi.summary });
  const critical = summary?.activeIncidents.critical ?? 0;

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-14 items-center px-4">
        <Link href="/dashboard" onClick={onNavigate} aria-label="Dispatch dashboard">
          <DispatchLogo />
        </Link>
      </div>
      <nav className="flex-1 space-y-0.5 px-2 py-3" aria-label="Main">
        {NAV.map(({ href, label, Icon, badge }) => {
          const active = pathname === href || pathname.startsWith(`${href}/`);
          return (
            <Link
              key={href}
              href={href}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={cn(
                "group flex h-9 items-center gap-3 rounded-md px-2.5 text-sm transition-colors",
                active ? "bg-surface-3 font-medium text-fg" : "text-fg-muted hover:bg-surface-2 hover:text-fg",
              )}
            >
              <Icon className={cn("size-4", active ? "text-accent" : "text-fg-subtle group-hover:text-fg-muted")} aria-hidden />
              <span className="flex-1">{label}</span>
              {badge === "incidents" && summary && summary.activeIncidents.total > 0 && (
                <span
                  className={cn(
                    "tabular rounded px-1.5 py-px text-[11px] font-semibold",
                    critical > 0 ? "bg-sev-critical/15 text-sev-critical" : "bg-surface-3 text-fg-muted",
                  )}
                  title={`${summary.activeIncidents.total} active, ${critical} critical`}
                >
                  {summary.activeIncidents.total}
                </span>
              )}
            </Link>
          );
        })}
      </nav>
      <div className="border-t border-line p-3">
        {me ? (
          <div className="flex items-center gap-3 rounded-md px-1.5 py-1">
            <Avatar name={me.name} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-fg">{me.name}</p>
              <p className="truncate text-xs text-fg-subtle">{me.role === "ADMIN" ? "Administrator" : "Operator"}</p>
            </div>
            <button
              type="button"
              onClick={signOut}
              title="Sign out"
              aria-label="Sign out"
              className="cursor-pointer rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-surface-3 hover:text-fg"
            >
              <LogOut className="size-4" />
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-3 px-1.5 py-1">
            <div className="size-7 animate-pulse rounded-full bg-surface-3" />
            <div className="h-4 flex-1 animate-pulse rounded bg-surface-3" />
          </div>
        )}
      </div>
    </div>
  );
}

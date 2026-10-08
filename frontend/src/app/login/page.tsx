import { Activity, GitMerge, Radio } from "lucide-react";
import type { Metadata } from "next";
import { Suspense } from "react";

import { LoginForm } from "@/components/auth/login-form";
import { DispatchLogo } from "@/components/shell/logo";

export const metadata: Metadata = { title: "Sign in" };

const highlights = [
  { Icon: Radio, title: "Live by default", text: "Events and incidents stream in over WebSockets — no refresh, no polling." },
  { Icon: GitMerge, title: "One incident per problem", text: "Repeated alerts fold into the active incident instead of paging you again." },
  { Icon: Activity, title: "Recovery aware", text: "Problems that clear themselves close themselves — unless someone is on it." },
];

export default function LoginPage() {
  return (
    <main className="relative grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_right,#ffffff06_1px,transparent_1px),linear-gradient(to_bottom,#ffffff06_1px,transparent_1px)] bg-[size:48px_48px] [mask-image:radial-gradient(ellipse_at_30%_40%,black,transparent_75%)]"
      />
      <section className="relative hidden flex-col justify-between border-r border-line p-12 lg:flex">
        <DispatchLogo />
        <div className="max-w-md">
          <p className="text-xs font-medium uppercase tracking-[0.18em] text-accent">Incident operations console</p>
          <h1 className="mt-4 text-4xl font-semibold leading-tight tracking-tight text-fg">
            Every signal.
            <br />
            One console.
          </h1>
          <ul className="mt-10 space-y-6">
            {highlights.map(({ Icon, title, text }) => (
              <li key={title} className="flex gap-4">
                <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-md border border-line-strong bg-surface text-accent">
                  <Icon className="size-4" aria-hidden />
                </span>
                <div>
                  <p className="text-sm font-medium text-fg">{title}</p>
                  <p className="mt-1 text-sm text-fg-muted">{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-fg-subtle">Dispatch · event ingestion → rule evaluation → incidents → resolution</p>
      </section>
      <section className="relative flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-sm">
          <DispatchLogo className="mb-8 lg:hidden" />
          <Suspense>
            <LoginForm />
          </Suspense>
        </div>
      </section>
    </main>
  );
}

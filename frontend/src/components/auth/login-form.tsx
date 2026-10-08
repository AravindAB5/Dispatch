"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Clock3, KeyRound, LogIn } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { InlineError } from "@/components/ui/states";
import { authApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import { saveSession } from "@/lib/auth/session";
import { SHOW_DEMO_CREDENTIALS } from "@/lib/config";
import { qk } from "@/lib/query-keys";

const DEMO_ACCOUNTS = [
  { label: "Admin", email: "admin@dispatch.local", password: "admin123", hint: "Manage rules, services, users, simulator" },
  { label: "Operator", email: "operator@dispatch.local", password: "operator123", hint: "Work incidents" },
];

/**
 * Only same-origin paths are allowed as post-login destinations (no open redirects). The value is
 * resolved exactly as the browser would resolve it, so tricks like "/\evil.example" or "/\t/evil.example"
 * (which browsers normalise to "//evil.example") are caught by the origin check.
 */
export function safeNextPath(next: string | null, origin: string = window.location.origin): string {
  const fallback = "/dashboard";
  if (!next || !next.startsWith("/")) return fallback;
  try {
    const url = new URL(next, origin);
    if (url.origin !== origin || url.pathname.startsWith("/login")) return fallback;
    return url.pathname + url.search + url.hash;
  } catch {
    return fallback;
  }
}

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const expired = searchParams.get("reason") === "expired";

  const login = useMutation({
    mutationFn: () => authApi.login(email.trim(), password),
    onSuccess: (session) => {
      saveSession(session.accessToken, session.expiresAt);
      queryClient.setQueryData(qk.me, session.user);
      router.replace(safeNextPath(searchParams.get("next")));
    },
  });

  const fieldErrors = login.error instanceof ApiError ? login.error.fieldErrors : {};

  function submit(event: FormEvent) {
    event.preventDefault();
    login.mutate();
  }

  return (
    <div>
      <h2 className="text-xl font-semibold tracking-tight text-fg">Sign in</h2>
      <p className="mt-1 text-sm text-fg-muted">Use your Dispatch account to open the console.</p>

      {expired && !login.error && (
        <div className="mt-5 flex items-center gap-2 rounded-md border border-line-strong bg-surface px-3 py-2 text-sm text-fg-muted">
          <Clock3 className="size-4 shrink-0 text-warn" aria-hidden />
          Your session expired. Sign in again to continue.
        </div>
      )}

      <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
        <Field label="Email" error={fieldErrors.email}>
          {(props) => (
            <Input
              {...props}
              type="email"
              autoComplete="username"
              placeholder="you@company.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
            />
          )}
        </Field>
        <Field label="Password" error={fieldErrors.password}>
          {(props) => (
            <Input
              {...props}
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          )}
        </Field>

        {login.error && Object.keys(fieldErrors).length === 0 && <InlineError error={login.error} />}

        <Button
          type="submit"
          variant="primary"
          className="w-full"
          loading={login.isPending || login.isSuccess}
          icon={<LogIn className="size-4" aria-hidden />}
        >
          Sign in
        </Button>
      </form>

      {SHOW_DEMO_CREDENTIALS && (
        <div className="mt-8 rounded-lg border border-line bg-surface p-4">
          <p className="flex items-center gap-2 text-xs font-medium text-fg-muted">
            <KeyRound className="size-3.5" aria-hidden /> Demo accounts (development only)
          </p>
          <div className="mt-3 grid gap-2">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                key={account.email}
                type="button"
                aria-label={`Use the ${account.label} demo account`}
                onClick={() => {
                  setEmail(account.email);
                  setPassword(account.password);
                  login.reset();
                }}
                className="group flex cursor-pointer items-center justify-between gap-3 rounded-md border border-line-strong bg-canvas/60 px-3 py-2 text-left transition-colors hover:border-accent/50"
              >
                <span className="min-w-0">
                  <span className="block text-sm font-medium text-fg">{account.label}</span>
                  <span className="block truncate font-mono text-[11px] text-fg-subtle">
                    {account.email} · {account.password}
                  </span>
                </span>
                <span className="shrink-0 text-xs text-fg-subtle group-hover:text-accent">Use</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

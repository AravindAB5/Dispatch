import { SESSION_COOKIE } from "@/lib/config";

/*
 * The access token lives in a first-party cookie so that the Next.js proxy can redirect
 * signed-out visitors before any page renders. The API itself only accepts the token as an
 * `Authorization: Bearer` header (never from cookies), so the cookie cannot be used for CSRF.
 */

export function getToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.split("; ").find((c) => c.startsWith(`${SESSION_COOKIE}=`));
  return match ? decodeURIComponent(match.slice(SESSION_COOKIE.length + 1)) : null;
}

export function saveSession(token: string, expiresAt: string) {
  const maxAge = Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `${SESSION_COOKIE}=${encodeURIComponent(token)}; Path=/; Max-Age=${maxAge}; SameSite=Strict${secure}`;
}

export function clearSession() {
  document.cookie = `${SESSION_COOKIE}=; Path=/; Max-Age=0; SameSite=Strict`;
}

/** Sends the user to the login page, remembering where they were. */
export function redirectToLogin(reason?: "expired" | "signed-out") {
  clearSession();
  const here = window.location.pathname + window.location.search;
  const params = new URLSearchParams();
  if (here && here !== "/login") params.set("next", here);
  if (reason) params.set("reason", reason);
  // A full navigation deliberately drops all in-memory state of the expired session.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign(`/login${params.size ? `?${params}` : ""}`);
}

export type TokenClaims = {
  sub?: string;
  exp?: number;
  name?: string;
  email?: string;
  roles?: string[];
};

/**
 * Reads (does NOT verify) a JWT payload. Used only for optimistic UI decisions such as
 * "is this session obviously expired?" — the API verifies every token it receives.
 */
export function decodeClaims(token: string | undefined | null): TokenClaims | null {
  if (!token) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    return JSON.parse(atob(padded)) as TokenClaims;
  } catch {
    return null;
  }
}

export function isExpired(token: string | undefined | null, nowMs: number = Date.now()): boolean {
  const claims = decodeClaims(token);
  if (!claims?.exp) return true;
  // Treat tokens within 10 seconds of expiry as expired to avoid racing the server.
  return claims.exp * 1000 - 10_000 <= nowMs;
}

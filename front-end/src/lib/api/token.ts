/**
 * Browser-side access token for the Spring services. Better Auth mints a short-lived JWT for the
 * current session at `/api/auth/token` (cookie-authenticated); we cache it in memory and refresh a
 * minute before it expires. Never persisted (FRONTEND.md §7 — no tokens in localStorage).
 *
 * Server components use `getAccessToken()` from `@/lib/auth/session` instead.
 */
let cached: { token: string; expiresAt: number } | null = null;
let inFlight: Promise<string | null> | null = null;

export async function getClientAccessToken(): Promise<string | null> {
  if (cached && cached.expiresAt - 60_000 > Date.now()) return cached.token;
  inFlight ??= fetchToken().finally(() => {
    inFlight = null;
  });
  return inFlight;
}

/** Forget the cached token — call on sign-out so the next request starts clean. */
export function clearClientAccessToken(): void {
  cached = null;
}

async function fetchToken(): Promise<string | null> {
  try {
    const response = await fetch("/api/auth/token", { credentials: "include" });
    if (!response.ok) return null;
    const { token } = (await response.json()) as { token?: string };
    if (!token) return null;
    cached = { token, expiresAt: expiryOf(token) };
    return token;
  } catch {
    return null;
  }
}

function expiryOf(jwt: string): number {
  try {
    const payload = JSON.parse(atob(jwt.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/"))) as { exp?: number };
    return payload.exp ? payload.exp * 1000 : Date.now() + 5 * 60_000;
  } catch {
    return Date.now() + 5 * 60_000;
  }
}

import { supabaseBrowser } from "@/lib/supabase/browser";
import { isSupabaseAuthConfigured } from "@/lib/supabase/env";

/**
 * Browser-side access token for the Spring services: the Supabase session's JWT, which the SDK
 * refreshes from the cookie when expired. Cached in memory and asked for again a minute before
 * expiry. Never persisted by us (FRONTEND.md §7 — no tokens in localStorage).
 *
 * Server components use `getAccessToken()` from `@/lib/auth/session` instead.
 */
let cached: { token: string; expiresAt: number } | null = null;
let inFlight: Promise<string | null> | null = null;

export async function getClientAccessToken(): Promise<string | null> {
  if (!isSupabaseAuthConfigured()) return null;
  if (cached && cached.expiresAt - 60_000 > Date.now()) return cached.token;
  inFlight ??= fetchToken().finally(() => {
    inFlight = null;
  });
  return inFlight;
}

/** Forget the cached token — the account menu calls this before sign-out. */
export function clearClientAccessToken(): void {
  cached = null;
}

async function fetchToken(): Promise<string | null> {
  try {
    const { data } = await supabaseBrowser().auth.getSession();
    const session = data.session;
    if (!session?.access_token) return null;
    cached = { token: session.access_token, expiresAt: session.expires_at ? session.expires_at * 1000 : Date.now() + 5 * 60_000 };
    return session.access_token;
  } catch {
    return null;
  }
}

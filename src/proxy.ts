import { NextResponse, type NextRequest } from "next/server";
import createIntlProxy from "next-intl/middleware";
import { createServerClient } from "@supabase/ssr";
import { isSupabaseAuthConfigured, SUPABASE_ANON_KEY, SUPABASE_URL } from "./lib/supabase/env";
import { routing } from "./i18n/routing";

const intl = createIntlProxy(routing);

/**
 * Two jobs at the network boundary (Next 16 "proxy", formerly middleware):
 *
 * 1. Supabase Auth — refreshes an expired access token and writes the new session cookies to the
 *    response. Server components can't set cookies, so this is the one place a refresh can land.
 * 2. Locale negotiation — /home → /en/home, /bg/home passes through.
 *
 * No auth gate on purpose: the app is browsable as a guest. Pages that need an account decide for
 * themselves what to show one; only the `(auth)` layout redirects signed-in visitors away.
 */
export default async function proxy(request: NextRequest) {
  const response: NextResponse = intl(request);
  if (!isSupabaseAuthConfigured()) {
    warnUnconfiguredOnce();
    return response;
  }

  const supabase = createServerClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    cookies: {
      getAll: () => request.cookies.getAll(),
      setAll: (toSet) => {
        for (const { name, value, options } of toSet) response.cookies.set(name, value, options);
      },
    },
  });
  // Verifies the JWT (and refreshes it when expired) — the cookies it sets ride on `response`.
  await supabase.auth.getClaims();
  return response;
}

let warned = false;
function warnUnconfiguredOnce() {
  if (warned) return;
  warned = true;
  console.warn("[auth] NEXT_PUBLIC_SUPABASE_URL / NEXT_PUBLIC_SUPABASE_ANON_KEY are not set — running as guest only. See .env.example.");
}

export const config = {
  // Skip Next internals, static files and our own API routes (/api/auth, /api/avatars, /api/profile).
  matcher: ["/((?!api|_next|_vercel|.*\..*).*)"],
};

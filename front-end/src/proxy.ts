import createProxy from "next-intl/middleware";
import { routing } from "./i18n/routing";

/**
 * Locale negotiation at the edge (Next 16 "proxy", formerly middleware):
 *   /            → /en or /bg (Accept-Language / NEXT_LOCALE cookie)
 *   /home        → /en/home
 *   /bg/home     → passes through
 *
 * No auth gate on purpose: the app is browsable as a guest (search, play, artist/album pages).
 * Server components read the optional session with `getSession()`; pages that need an account
 * (library, likes, friends…) decide for themselves what to show a guest. Only the `(auth)` layout
 * redirects — signed-in visitors away from /login and friends.
 */
export default createProxy(routing);

export const config = {
  // Skip Next internals, static files and API routes (Better Auth lives under /api/auth).
  matcher: ["/((?!api|_next|_vercel|.*\\..*).*)"],
};

import createProxy from "next-intl/middleware";
import { routing } from "./i18n/routing";

/**
 * Locale negotiation at the edge (Next 16 "proxy", formerly middleware):
 *   /            → /en or /bg (Accept-Language / NEXT_LOCALE cookie)
 *   /home        → /en/home
 *   /bg/home     → passes through
 * Auth gating will be layered in here with the Auth slice.
 */
export default createProxy(routing);

export const config = {
  // Skip Next internals, static files and API routes.
  matcher: ["/((?!api|_next|_vercel|.*\\..*).*)"],
};

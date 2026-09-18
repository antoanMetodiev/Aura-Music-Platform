import "server-only";

import { cache } from "react";
import { headers } from "next/headers";
import { auth, type Session } from "./auth";

/**
 * The current session for this request, or `null`. Memoized per request (React `cache`) so the
 * layout, pages and API helpers all share one lookup — and with the cookie cache on, most requests
 * never touch the database at all.
 */
export const getSession = cache(async (): Promise<Session | null> => {
  return auth.api.getSession({ headers: await headers() });
});

/**
 * A fresh JWT for the current user, for calls to the Spring services (they verify it against
 * `/api/auth/jwks`). `null` when nobody is signed in.
 */
export const getAccessToken = cache(async (): Promise<string | null> => {
  try {
    const { token } = await auth.api.getToken({ headers: await headers() });
    return token ?? null;
  } catch {
    return null;
  }
});

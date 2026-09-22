import "server-only";

import { cookies } from "next/headers";
import { createServerClient } from "@supabase/ssr";
import { SUPABASE_ANON_KEY, SUPABASE_URL } from "./env";

/**
 * A Supabase client bound to this request's cookies — for server components, route handlers and
 * server actions. Reads the session from the cookie jar; writes (token refresh, sign-in) go back
 * through `cookies().set`, which Next allows only in route handlers / server actions. In a server
 * component the write is swallowed — the proxy already refreshed the session for this request.
 */
export async function createSupabaseServerClient() {
  const store = await cookies();
  return createServerClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    cookies: {
      getAll: () => store.getAll(),
      setAll: (toSet) => {
        try {
          for (const { name, value, options } of toSet) store.set(name, value, options);
        } catch {
          // Server component render: cookies are read-only here; see proxy.ts.
        }
      },
    },
  });
}

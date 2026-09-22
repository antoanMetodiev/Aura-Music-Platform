import { createBrowserClient } from "@supabase/ssr";
import { SUPABASE_ANON_KEY, SUPABASE_URL } from "./env";

/**
 * The browser-side Supabase client (one per tab — `createBrowserClient` is a singleton). Shares the
 * session cookie with the server side, so a sign-in here is visible to the next server render.
 */
export function supabaseBrowser() {
  return createBrowserClient(SUPABASE_URL, SUPABASE_ANON_KEY);
}

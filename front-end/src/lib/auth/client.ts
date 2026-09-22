import { supabaseBrowser } from "@/lib/supabase/browser";
import { clearClientAccessToken } from "@/lib/api/token";

/**
 * Ends the session in this browser (`scope: "local"`) or on every device (`"global"`), then sends
 * the browser to `returnTo` with a full load so every server component re-reads the cookie.
 */
export async function signOut(returnTo: string, scope: "local" | "global" = "local"): Promise<void> {
  clearClientAccessToken();
  await supabaseBrowser().auth.signOut({ scope });
  window.location.assign(returnTo);
}

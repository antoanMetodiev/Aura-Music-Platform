import "server-only";

import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { SUPABASE_URL } from "./env";

/**
 * Service-role client — bypasses RLS and unlocks `auth.admin.*` (delete a user, read a record by
 * id). Only for the few operations a user cannot do to their own account through the anon client;
 * never import from anything the browser can reach.
 */
let client: SupabaseClient | null = null;

export function supabaseAdmin(): SupabaseClient {
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!key) throw new Error("Missing environment variable SUPABASE_SERVICE_ROLE_KEY");
  client ??= createClient(SUPABASE_URL, key, { auth: { autoRefreshToken: false, persistSession: false } });
  return client;
}

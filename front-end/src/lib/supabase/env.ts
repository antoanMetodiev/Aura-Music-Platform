/**
 * Supabase Auth — Aura's identity provider (Project-Info.md §8, ADR-012). The URL and the anon
 * (publishable) key are public by design: the browser client uses them too, and Row Level
 * Security / the auth API guard what they can reach. The service-role key is server-only.
 */
export const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
export const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";

/**
 * True once the project keys are in .env.local. Until then the app runs in guest-only mode
 * instead of failing every request — the proxy skips the session refresh and `getSession()`
 * returns null.
 */
export function isSupabaseAuthConfigured(): boolean {
  return Boolean(SUPABASE_URL && SUPABASE_ANON_KEY);
}

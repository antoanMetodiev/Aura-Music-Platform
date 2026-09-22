import { NextResponse, type NextRequest } from "next/server";
import { createSupabaseServerClient } from "@/lib/supabase/server";
import { isSupabaseAuthConfigured } from "@/lib/supabase/env";
import { safePath } from "@/lib/auth/redirects";

/**
 * Where Supabase sends the browser back after Google sign-in, an email confirmation link or a
 * password-reset link (PKCE flow): `?code=` is exchanged for a session, whose cookies are set
 * here, then the user lands on `?next=` (same-site only, e.g. `/en/home`, `/bg/reset-password`).
 * Registered in the Supabase dashboard under Authentication → URL Configuration → Redirect URLs.
 */
export async function GET(request: NextRequest) {
  const url = request.nextUrl;
  const code = url.searchParams.get("code");
  const next = safePath(url.searchParams.get("next"));

  if (code && isSupabaseAuthConfigured()) {
    const supabase = await createSupabaseServerClient();
    const { error } = await supabase.auth.exchangeCodeForSession(code);
    if (!error) return NextResponse.redirect(new URL(next, url.origin));
  }
  // Stale or reused link — the login page explains and offers to start over.
  const reason = url.searchParams.get("error_code") ?? url.searchParams.get("error") ?? "link";
  return NextResponse.redirect(new URL(`/login?error=${encodeURIComponent(reason)}`, url.origin));
}

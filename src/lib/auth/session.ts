import "server-only";

import { cache } from "react";
import { createSupabaseServerClient } from "@/lib/supabase/server";
import { isSupabaseAuthConfigured } from "@/lib/supabase/env";
import { ensureProfile, type Profile } from "./profile";
import type { UserSummary } from "@/types/social";

/** The signed-in user as Aura sees it: the Supabase identity plus our profile row. */
export interface AppUser {
  /** Supabase user id (uuid) — the canonical user id everywhere (Project-Info.md §8). */
  sub: string;
  email?: string;
  /** Display name from identity.user_profile. */
  name: string;
  username: string;
  /** The identity provider's picture (Google), if any. */
  picture?: string;
  /** Uploaded photo if any, else `picture`, else undefined. */
  avatarUrl?: string;
  /** Primary sign-in provider: "email" (password) or "google". */
  provider: string;
  createdAt: Date;
}

/**
 * The signed-in user for this request, or `null` for a guest. Memoized per request (React `cache`)
 * so the layout, pages and helpers share one lookup. The JWT in the session cookie is verified
 * locally (`getClaims`, JWKS cached), then joined with our profile row — one small query.
 */
export const getSession = cache(async (): Promise<{ user: AppUser } | null> => {
  if (!isSupabaseAuthConfigured()) return null;
  const supabase = await createSupabaseServerClient();
  const { data, error } = await supabase.auth.getClaims();
  if (error || !data?.claims) return null;

  const claims = data.claims;
  const metadata = (claims.user_metadata ?? {}) as UserMetadata;
  const appMetadata = (claims.app_metadata ?? {}) as { provider?: string };
  const profile = await ensureProfile({
    userId: claims.sub,
    email: claims.email,
    username: metadata.username,
    displayName: metadata.display_name ?? metadata.full_name ?? metadata.name,
    picture: metadata.avatar_url ?? metadata.picture,
  });
  return { user: toAppUser(profile, { email: claims.email, provider: appMetadata.provider ?? "email" }) };
});

/**
 * Access token for the Spring services (Supabase-signed JWT; the services verify it against
 * `<SUPABASE_URL>/auth/v1/.well-known/jwks.json`). `null` for a guest.
 */
export const getAccessToken = cache(async (): Promise<string | null> => {
  if (!isSupabaseAuthConfigured()) return null;
  const supabase = await createSupabaseServerClient();
  const { data } = await supabase.auth.getSession();
  return data.session?.access_token ?? null;
});

/** The shape the app shell and social components speak. */
export function toUserSummary(user: AppUser): UserSummary {
  return { id: user.sub, username: user.username, displayName: user.name, avatarUrl: user.avatarUrl };
}

export function toAppUser(profile: Profile, identity: { email?: string; provider: string }): AppUser {
  return {
    sub: profile.userId,
    email: identity.email,
    name: profile.displayName,
    username: profile.username,
    picture: profile.pictureUrl ?? undefined,
    avatarUrl: profile.avatarUrl ?? profile.pictureUrl ?? undefined,
    provider: identity.provider,
    createdAt: profile.createdAt,
  };
}

/** What sign-up (our form) and Google put in Supabase `user_metadata`. */
interface UserMetadata {
  username?: string;
  display_name?: string;
  full_name?: string;
  name?: string;
  avatar_url?: string;
  picture?: string;
}

import type { AuthUser } from "@/lib/auth/auth";
import type { UserSummary } from "@/types/social";

/** The shape the app shell and social components already speak, from a Better Auth user. */
export function toUserSummary(user: AuthUser): UserSummary {
  return {
    id: user.id,
    // Always set by the create hook (see auth.ts); the fallback only guards the type.
    username: user.username ?? user.id,
    displayName: user.name,
    avatarUrl: user.image ?? undefined,
  };
}

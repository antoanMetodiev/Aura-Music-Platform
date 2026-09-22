"use server";

import { usernameTaken } from "@/lib/auth/profile";
import { normalizeUsername, USERNAME_MAX, USERNAME_MIN, USERNAME_PATTERN } from "@/lib/auth/username";

/**
 * Sign-up asks before creating the account whether the username is free — the profile row is
 * only written on first sign-in (`ensureProfile`), which would silently suffix a taken name.
 * A race between two sign-ups picking the same name still ends in a suffix, never an error.
 */
export async function usernameAvailableAction(raw: string): Promise<{ ok: true } | { ok: false; code: "INVALID_USERNAME" | "USERNAME_TAKEN" }> {
  const username = normalizeUsername(raw);
  if (username.length < USERNAME_MIN || username.length > USERNAME_MAX || !USERNAME_PATTERN.test(username)) {
    return { ok: false, code: "INVALID_USERNAME" };
  }
  return (await usernameTaken(username)) ? { ok: false, code: "USERNAME_TAKEN" } : { ok: true };
}

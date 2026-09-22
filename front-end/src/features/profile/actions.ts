"use server";

import { revalidatePath } from "next/cache";
import { deleteAvatar } from "@/lib/auth/avatars";
import { deleteProfile, updateProfile, usernameTaken } from "@/lib/auth/profile";
import { getSession } from "@/lib/auth/session";
import { normalizeUsername, USERNAME_MAX, USERNAME_MIN, USERNAME_PATTERN } from "@/lib/auth/username";
import { supabaseAdmin } from "@/lib/supabase/admin";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export type ActionResult = { ok: true } | { ok: false; code: ActionErrorCode };
export type ActionErrorCode =
  | "UNAUTHORIZED"
  | "INVALID_NAME"
  | "INVALID_USERNAME"
  | "USERNAME_TAKEN"
  | "INVALID_EMAIL"
  | "EMAIL_NOT_EDITABLE"
  | "PASSWORD_NOT_APPLICABLE"
  | "PROVIDER_ERROR";

/**
 * Profile edits go through here. Display name and username are ours (identity.user_profile);
 * email, password and sessions belong to Supabase Auth and are changed through the user's own
 * session (the anon client), so nothing here needs the service-role key except deletion.
 */
export async function updateDetailsAction(input: { name: string; username: string }): Promise<ActionResult> {
  const session = await getSession();
  if (!session) return { ok: false, code: "UNAUTHORIZED" };
  const { user } = session;

  const name = input.name.trim();
  if (name.length < 1 || name.length > 60) return { ok: false, code: "INVALID_NAME" };
  const username = normalizeUsername(input.username);
  if (username.length < USERNAME_MIN || username.length > USERNAME_MAX || !USERNAME_PATTERN.test(username)) {
    return { ok: false, code: "INVALID_USERNAME" };
  }

  try {
    if (username !== user.username && (await usernameTaken(username, user.sub))) return { ok: false, code: "USERNAME_TAKEN" };
    await updateProfile(user.sub, { displayName: name, username });
    revalidatePath("/", "layout");
    return { ok: true };
  } catch (error) {
    console.error("[profile] updateDetails failed", error);
    return { ok: false, code: "PROVIDER_ERROR" };
  }
}

/**
 * The sign-in email. For email+password accounts Supabase sends a confirmation to the new address
 * (and, with secure email change on, to the old one); the change lands once confirmed. For Google
 * accounts the address *is* the Google account and can't be edited here.
 */
export async function changeEmailAction(newEmail: string): Promise<ActionResult> {
  const session = await getSession();
  if (!session) return { ok: false, code: "UNAUTHORIZED" };
  if (session.user.provider !== "email") return { ok: false, code: "EMAIL_NOT_EDITABLE" };

  const email = newEmail.trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return { ok: false, code: "INVALID_EMAIL" };

  try {
    const supabase = await createSupabaseServerClient();
    const { error } = await supabase.auth.updateUser({ email }, { emailRedirectTo: `${appOrigin()}/api/auth/callback` });
    if (error) throw error;
    return { ok: true };
  } catch (error) {
    console.error("[profile] changeEmail failed", error);
    return { ok: false, code: "PROVIDER_ERROR" };
  }
}

/** Emails a password-reset link. Only meaningful for email+password accounts. */
export async function sendPasswordResetAction(): Promise<ActionResult> {
  const session = await getSession();
  if (!session) return { ok: false, code: "UNAUTHORIZED" };
  const { user } = session;
  if (user.provider !== "email" || !user.email) return { ok: false, code: "PASSWORD_NOT_APPLICABLE" };

  try {
    const supabase = await createSupabaseServerClient();
    const { error } = await supabase.auth.resetPasswordForEmail(user.email, {
      redirectTo: `${appOrigin()}/api/auth/callback?next=${encodeURIComponent("/reset-password")}`,
    });
    if (error) throw error;
    return { ok: true };
  } catch (error) {
    console.error("[profile] password reset mail failed", error);
    return { ok: false, code: "PROVIDER_ERROR" };
  }
}

/** Re-sends the sign-up confirmation email for the current address. */
export async function resendVerificationAction(): Promise<ActionResult> {
  const session = await getSession();
  if (!session) return { ok: false, code: "UNAUTHORIZED" };
  const { user } = session;
  if (user.provider !== "email" || !user.email) return { ok: false, code: "EMAIL_NOT_EDITABLE" };

  try {
    const supabase = await createSupabaseServerClient();
    const { error } = await supabase.auth.resend({
      type: "signup",
      email: user.email,
      options: { emailRedirectTo: `${appOrigin()}/api/auth/callback` },
    });
    if (error) throw error;
    return { ok: true };
  } catch (error) {
    console.error("[profile] verification mail failed", error);
    return { ok: false, code: "PROVIDER_ERROR" };
  }
}

/**
 * Deletes the Supabase user (every session and identity with it) and our own data about them.
 * The caller then clears the local cookie with a client-side sign-out.
 */
export async function deleteAccountAction(): Promise<ActionResult> {
  const session = await getSession();
  if (!session) return { ok: false, code: "UNAUTHORIZED" };
  const { user } = session;

  try {
    await deleteAvatar(user.sub);
    await deleteProfile(user.sub);
    const { error } = await supabaseAdmin().auth.admin.deleteUser(user.sub);
    if (error) throw error;
    return { ok: true };
  } catch (error) {
    console.error("[profile] deleteAccount failed", error);
    return { ok: false, code: "PROVIDER_ERROR" };
  }
}

/** Where this app is served — email links must point back here, not at Supabase. */
function appOrigin(): string {
  return (process.env.NEXT_PUBLIC_APP_URL ?? "http://localhost:3000").replace(/\/$/, "");
}

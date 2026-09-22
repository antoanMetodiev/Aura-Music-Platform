import { useTranslations } from "next-intl";
import type { AuthError } from "@supabase/supabase-js";
import type messages from "../../../../messages/en.json";

export type AuthErrorKey = keyof typeof messages.auth.errors;

/**
 * Maps a Supabase Auth failure to an `auth.errors.*` message key. `code` is the stable identifier
 * (https://supabase.com/docs/guides/auth/debugging/error-codes); `message` is English prose.
 */
export function authErrorKey(error: Pick<AuthError, "code" | "status"> | null | undefined): AuthErrorKey {
  if (!error) return "generic";
  if (error.status === 429 || error.code === "over_request_rate_limit" || error.code === "over_email_send_rate_limit") {
    return "tooManyRequests";
  }
  switch (error.code) {
    case "invalid_credentials":
    case "user_not_found":
      return "invalidCredentials";
    case "email_not_confirmed":
      return "emailNotConfirmed";
    case "user_already_exists":
    case "email_exists":
      return "emailTaken";
    case "weak_password":
      return "passwordTooShort";
    case "same_password":
      return "samePassword";
    case "otp_expired":
    case "flow_state_expired":
    case "flow_state_not_found":
    case "bad_code_verifier":
      return "linkExpired";
    case "signup_disabled":
    case "email_provider_disabled":
      return "signupDisabled";
    default:
      return "generic";
  }
}

/**
 * Translators for the auth forms: `field(err)` turns a Zod message (which is an `auth.errors` key,
 * see schemas/auth.ts) into text, `fromApi(err)` does the same for a Supabase failure.
 */
export function useAuthErrorText() {
  const t = useTranslations("auth");
  return {
    field: (error?: { message?: string }) => (error?.message ? t(`errors.${error.message as AuthErrorKey}`) : undefined),
    fromApi: (error: Pick<AuthError, "code" | "status"> | null | undefined) => t(`errors.${authErrorKey(error)}`),
  };
}

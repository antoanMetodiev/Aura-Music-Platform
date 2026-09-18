import { useTranslations } from "next-intl";
import type messages from "../../../../messages/en.json";

export type AuthErrorKey = keyof typeof messages.auth.errors;

/**
 * Maps a Better Auth failure to an `auth.errors.*` message key. The client returns
 * `{ code, message, status }`; codes are stable identifiers, messages are English prose.
 */
export function authErrorKey(error: { code?: string; status?: number } | null | undefined): AuthErrorKey {
  if (!error) return "generic";
  if (error.status === 429) return "tooManyRequests";
  switch (error.code) {
    case "INVALID_EMAIL_OR_PASSWORD":
    case "INVALID_PASSWORD":
    case "USER_NOT_FOUND":
      return "invalidCredentials";
    case "USER_ALREADY_EXISTS":
    case "USER_ALREADY_EXISTS_USE_ANOTHER_EMAIL":
      return "emailTaken";
    case "USERNAME_IS_ALREADY_TAKEN":
      return "usernameTaken";
    case "USERNAME_TOO_SHORT":
      return "usernameTooShort";
    case "USERNAME_TOO_LONG":
      return "usernameTooLong";
    case "INVALID_USERNAME":
      return "usernameInvalid";
    case "PASSWORD_TOO_SHORT":
      return "passwordTooShort";
    case "PASSWORD_TOO_LONG":
      return "passwordTooLong";
    default:
      return "generic";
  }
}

/**
 * Translators for the auth forms: `field(err)` turns a Zod message (which is an `auth.errors` key,
 * see schemas/auth.ts) into text, `fromApi(err)` does the same for a Better Auth failure.
 */
export function useAuthErrorText() {
  const t = useTranslations("auth");
  return {
    field: (error?: { message?: string }) => (error?.message ? t(`errors.${error.message as AuthErrorKey}`) : undefined),
    fromApi: (error: { code?: string; status?: number } | null | undefined) => t(`errors.${authErrorKey(error)}`),
  };
}

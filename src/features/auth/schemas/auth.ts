import { z } from "zod";
import { USERNAME_MAX, USERNAME_MIN, USERNAME_PATTERN } from "@/lib/auth/username";

/**
 * Zod schemas for the auth forms. Messages are i18n keys under `auth.errors` — the forms
 * translate them, so the same schema serves both locales.
 */
const email = z.string().trim().min(1, "emailRequired").email("emailInvalid");
const password = z.string().min(8, "passwordTooShort").max(128, "passwordTooLong");

export const loginSchema = z.object({
  email,
  password: z.string().min(1, "passwordRequired"),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  displayName: z.string().trim().min(1, "displayNameRequired").max(60, "displayNameTooLong"),
  username: z
    .string()
    .trim()
    .toLowerCase()
    .min(USERNAME_MIN, "usernameTooShort")
    .max(USERNAME_MAX, "usernameTooLong")
    .regex(USERNAME_PATTERN, "usernameInvalid"),
  email,
  password,
});
export type RegisterValues = z.infer<typeof registerSchema>;

export const forgotPasswordSchema = z.object({ email });
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({ password, confirmPassword: z.string() })
  .refine((v) => v.password === v.confirmPassword, { path: ["confirmPassword"], message: "passwordsDiffer" });
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;

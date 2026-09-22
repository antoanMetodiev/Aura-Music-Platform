"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { FormError } from "@/components/common/FormError";
import { FormField } from "@/components/common/FormField";
import { useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { supabaseBrowser } from "@/lib/supabase/browser";
import { usernameAvailableAction } from "../actions";
import { callbackUrl } from "../lib/callback";
import { useAuthErrorText } from "../lib/errors";
import { registerSchema, type RegisterValues } from "../schemas/auth";
import { OAuthButtons } from "./OAuthButtons";

export function RegisterForm() {
  const t = useTranslations("auth");
  const errorText = useAuthErrorText();
  const locale = useLocale();
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);
  const [confirmSentTo, setConfirmSentTo] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) });

  const onSubmit = async (values: RegisterValues) => {
    setFormError(null);
    const availability = await usernameAvailableAction(values.username);
    if (!availability.ok) {
      setError("username", { message: availability.code === "USERNAME_TAKEN" ? "usernameTaken" : "usernameInvalid" });
      return;
    }

    // username / display_name ride along in user_metadata; the profile row is created from them on
    // the first signed-in request (lib/auth/profile.ts ensureProfile).
    const { data, error } = await supabaseBrowser().auth.signUp({
      email: values.email,
      password: values.password,
      options: {
        data: { username: values.username, display_name: values.displayName },
        emailRedirectTo: callbackUrl(`/${locale}${routes.home}`),
      },
    });
    if (error) {
      setFormError(errorText.fromApi(error));
      return;
    }
    if (data.session) {
      // Email confirmation is off on the project: signed in straight away.
      router.replace(routes.home);
      router.refresh();
      return;
    }
    // With confirmation on, an already-registered email comes back as a user with no identities
    // (Supabase avoids enumeration); say so instead of promising a mail that never comes.
    if (data.user && data.user.identities?.length === 0) {
      setFormError(t("errors.emailTaken"));
      return;
    }
    setConfirmSentTo(values.email);
  };

  if (confirmSentTo) {
    return (
      <p className="rounded-lg border border-border bg-elevated/60 px-4 py-3 text-sm">
        {t("register.confirmSent", { email: confirmSentTo })}
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <OAuthButtons />
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
        <FormField
          label={t("fields.displayName")}
          autoComplete="name"
          error={errorText.field(errors.displayName)}
          {...register("displayName")}
        />
        <FormField
          label={t("fields.username")}
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          hint={t("register.usernameHint")}
          error={errorText.field(errors.username)}
          {...register("username")}
        />
        <FormField
          label={t("fields.email")}
          type="email"
          autoComplete="email"
          error={errorText.field(errors.email)}
          {...register("email")}
        />
        <FormField
          label={t("fields.password")}
          type="password"
          autoComplete="new-password"
          hint={t("register.passwordHint")}
          error={errorText.field(errors.password)}
          {...register("password")}
        />
        <FormError message={formError} />
        <Button type="submit" size="lg" className="h-10 w-full" disabled={isSubmitting}>
          {t("register.submit")}
        </Button>
      </form>
    </div>
  );
}

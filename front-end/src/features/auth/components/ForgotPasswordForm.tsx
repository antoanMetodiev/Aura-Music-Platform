"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { FormError } from "@/components/common/FormError";
import { FormField } from "@/components/common/FormField";
import { routes } from "@/config/routes";
import { supabaseBrowser } from "@/lib/supabase/browser";
import { callbackUrl } from "../lib/callback";
import { useAuthErrorText } from "../lib/errors";
import { forgotPasswordSchema, type ForgotPasswordValues } from "../schemas/auth";

export function ForgotPasswordForm() {
  const t = useTranslations("auth");
  const errorText = useAuthErrorText();
  const locale = useLocale();
  const [formError, setFormError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordValues>({ resolver: zodResolver(forgotPasswordSchema) });

  const onSubmit = async (values: ForgotPasswordValues) => {
    setFormError(null);
    // The link in the mail comes back through /api/auth/callback, which opens a recovery session
    // and lands on /reset-password.
    const { error } = await supabaseBrowser().auth.resetPasswordForEmail(values.email, {
      redirectTo: callbackUrl(`/${locale}${routes.resetPassword}`),
    });
    if (error) {
      setFormError(errorText.fromApi(error));
      return;
    }
    // Same message whether or not the address exists — no account enumeration.
    setSentTo(values.email);
  };

  if (sentTo) {
    return (
      <p className="rounded-lg border border-border bg-elevated/60 px-4 py-3 text-sm">{t("forgot.sent", { email: sentTo })}</p>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
      <FormField
        label={t("fields.email")}
        type="email"
        autoComplete="email"
        error={errorText.field(errors.email)}
        {...register("email")}
      />
      <FormError message={formError} />
      <Button type="submit" size="lg" className="h-10 w-full" disabled={isSubmitting}>
        {t("forgot.submit")}
      </Button>
    </form>
  );
}

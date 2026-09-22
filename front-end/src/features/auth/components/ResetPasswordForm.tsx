"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { FormError } from "@/components/common/FormError";
import { FormField } from "@/components/common/FormField";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { supabaseBrowser } from "@/lib/supabase/browser";
import { useAuthErrorText } from "../lib/errors";
import { resetPasswordSchema, type ResetPasswordValues } from "../schemas/auth";

/**
 * Landing form for the link in the reset email. /api/auth/callback has already turned the link
 * into a signed-in (recovery) session by the time this renders; without one the link was stale.
 */
export function ResetPasswordForm() {
  const t = useTranslations("auth");
  const errorText = useAuthErrorText();
  const locale = useLocale();
  const [hasSession, setHasSession] = useState<boolean | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({ resolver: zodResolver(resetPasswordSchema) });

  useEffect(() => {
    supabaseBrowser()
      .auth.getSession()
      .then(({ data }) => setHasSession(Boolean(data.session)));
  }, []);

  if (hasSession === null) return null;

  if (!hasSession) {
    return (
      <div className="flex flex-col gap-4">
        <FormError message={t("reset.invalid")} />
        <Button variant="outline" size="lg" className="h-10 w-full" render={<Link href={routes.forgotPassword} />}>
          {t("forgot.title")}
        </Button>
      </div>
    );
  }

  if (done) {
    return (
      <div className="flex flex-col gap-4">
        <p className="rounded-lg border border-border bg-elevated/60 px-4 py-3 text-sm">{t("reset.done")}</p>
        <Button size="lg" className="h-10 w-full" nativeButton={false} render={<a href={`/${locale}${routes.home}`} />}>
          {t("reset.continue")}
        </Button>
      </div>
    );
  }

  const onSubmit = async (values: ResetPasswordValues) => {
    setFormError(null);
    const { error } = await supabaseBrowser().auth.updateUser({ password: values.password });
    if (error) {
      setFormError(errorText.fromApi(error));
      return;
    }
    setDone(true);
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
      <FormField
        label={t("fields.password")}
        type="password"
        autoComplete="new-password"
        hint={t("register.passwordHint")}
        error={errorText.field(errors.password)}
        {...register("password")}
      />
      <FormField
        label={t("fields.confirmPassword")}
        type="password"
        autoComplete="new-password"
        error={errorText.field(errors.confirmPassword)}
        {...register("confirmPassword")}
      />
      <FormError message={formError} />
      <Button type="submit" size="lg" className="h-10 w-full" disabled={isSubmitting}>
        {t("reset.submit")}
      </Button>
    </form>
  );
}

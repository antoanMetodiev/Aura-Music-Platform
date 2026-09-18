"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { authClient } from "@/lib/auth/client";
import { resetPasswordSchema, type ResetPasswordValues } from "../schemas/auth";
import { useAuthErrorText } from "../lib/errors";
import { FormError } from "./FormError";
import { FormField } from "./FormField";

/** Landing form for the link in the reset email (`?token=`). */
export function ResetPasswordForm() {
  const t = useTranslations("auth");
  const errorText = useAuthErrorText();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({ resolver: zodResolver(resetPasswordSchema) });

  // Better Auth lands here with `?error=INVALID_TOKEN` when the link is stale.
  if (!token || searchParams.get("error")) {
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
        <Button size="lg" className="h-10 w-full" render={<Link href={routes.login} />}>
          {t("login.submit")}
        </Button>
      </div>
    );
  }

  const onSubmit = async (values: ResetPasswordValues) => {
    setFormError(null);
    const { error } = await authClient.resetPassword({ newPassword: values.password, token });
    if (error) {
      setFormError(error.code === "INVALID_TOKEN" ? t("reset.invalid") : errorText.fromApi(error));
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

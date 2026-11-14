"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { FormError } from "@/components/common/FormError";
import { FormField } from "@/components/common/FormField";
import { Link, useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { safePath } from "@/lib/auth/redirects";
import { supabaseBrowser } from "@/lib/supabase/browser";
import { loginSchema, type LoginValues } from "../schemas/auth";
import { useAuthErrorText } from "../lib/errors";
import { OAuthButtons } from "./OAuthButtons";

export function LoginForm() {
  const t = useTranslations("auth");
  const errorText = useAuthErrorText();
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safePath(searchParams.get("next"), routes.home);
  // /api/auth/callback lands here with ?error= when a link from an email is stale or reused.
  const [formError, setFormError] = useState<string | null>(searchParams.get("error") ? t("errors.linkExpired") : null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (values: LoginValues) => {
    setFormError(null);
    const { error } = await supabaseBrowser().auth.signInWithPassword({ email: values.email, password: values.password });
    if (error) {
      setFormError(errorText.fromApi(error));
      return;
    }
    // The session cookie is set; server components re-read it on the navigation + refresh.
    router.replace(next);
    router.refresh();
  };

  return (
    <div className="flex flex-col gap-5">
      <OAuthButtons next={next} />
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
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
          autoComplete="current-password"
          error={errorText.field(errors.password)}
          {...register("password")}
        />
        <FormError message={formError} />
        <Link href={routes.forgotPassword} className="self-start text-sm text-muted-foreground hover:text-foreground">
          {t("login.forgot")}
        </Link>
        <Button type="submit" size="lg" className="h-10 w-full" disabled={isSubmitting}>
          {t("login.submit")}
        </Button>
      </form>
    </div>
  );
}

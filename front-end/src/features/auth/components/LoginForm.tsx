"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Link, useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { signIn } from "@/lib/auth/client";
import { loginSchema, type LoginValues } from "../schemas/auth";
import { useAuthErrorText } from "../lib/errors";
import { FormError } from "./FormError";
import { FormField } from "./FormField";
import { OAuthButtons } from "./OAuthButtons";

export function LoginForm() {
  const t = useTranslations("auth");
  const errorText = useAuthErrorText();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [formError, setFormError] = useState<string | null>(
    searchParams.get("error") ? t("errors.generic") : null,
  );
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (values: LoginValues) => {
    setFormError(null);
    const { error } = await signIn.email({ email: values.email, password: values.password });
    if (error) {
      setFormError(errorText.fromApi(error));
      return;
    }
    // `next` is where the proxy sent us from; server components re-read the cookie on refresh.
    router.replace(safeNext(searchParams.get("next")) ?? routes.home);
    router.refresh();
  };

  return (
    <div className="flex flex-col gap-5">
      <OAuthButtons />
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

/** Only same-site paths — never an absolute URL someone put in the query string. */
function safeNext(value: string | null): string | null {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : null;
}

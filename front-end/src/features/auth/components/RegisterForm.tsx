"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { signUp } from "@/lib/auth/client";
import { registerSchema, type RegisterValues } from "../schemas/auth";
import { useAuthErrorText } from "../lib/errors";
import { FormError } from "./FormError";
import { FormField } from "./FormField";
import { OAuthButtons } from "./OAuthButtons";

export function RegisterForm() {
  const t = useTranslations("auth");
  const errorText = useAuthErrorText();
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) });

  const onSubmit = async (values: RegisterValues) => {
    setFormError(null);
    const { error } = await signUp.email({
      name: values.displayName,
      email: values.email,
      password: values.password,
      username: values.username,
      displayUsername: values.username,
    });
    if (error) {
      setFormError(errorText.fromApi(error));
      return;
    }
    // Signed in straight away (requireEmailVerification is off); the verification mail still goes out.
    router.replace(routes.home);
    router.refresh();
  };

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

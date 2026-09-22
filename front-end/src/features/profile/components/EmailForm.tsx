"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { FormError } from "@/components/common/FormError";
import { FormField } from "@/components/common/FormField";
import type { AppUser } from "@/lib/auth/session";
import { changeEmailAction, resendVerificationAction } from "../actions";
import { useActionErrorText } from "../lib/errors";
import { FormSuccess, SettingsSection } from "./SettingsSection";

/**
 * The sign-in email. Editable for email+password accounts (Supabase confirms the new address
 * before switching); read-only for Google accounts, where the address belongs to Google.
 */
export function EmailForm({ user, emailVerified }: { user: AppUser; emailVerified: boolean }) {
  const t = useTranslations("profile.email");
  const errorText = useActionErrorText();
  const router = useRouter();
  const [newEmail, setNewEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const editable = user.provider === "email";

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setNotice(null);
    startTransition(async () => {
      const result = await changeEmailAction(newEmail);
      if (!result.ok) {
        setError(errorText(result));
        return;
      }
      setNotice(t("changed"));
      setNewEmail("");
      router.refresh();
    });
  };

  const resend = () => {
    setError(null);
    setNotice(null);
    startTransition(async () => {
      const result = await resendVerificationAction();
      if (!result.ok) setError(errorText(result));
      else setNotice(t("resent", { email: user.email ?? "" }));
    });
  };

  return (
    <SettingsSection title={t("title")} description={editable ? undefined : t("managedByGoogle")}>
      <div className="flex max-w-md flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">{t("current")}</span>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm">{user.email}</span>
            <Badge variant={emailVerified ? "secondary" : "outline"}>{emailVerified ? t("verified") : t("unverified")}</Badge>
            {editable && !emailVerified && (
              <Button type="button" variant="link" size="sm" className="h-auto px-0" onClick={resend} disabled={pending}>
                {t("resend")}
              </Button>
            )}
          </div>
        </div>
        {editable && (
          <form onSubmit={submit} noValidate className="flex flex-col gap-4">
            <FormField
              label={t("new")}
              type="email"
              autoComplete="email"
              value={newEmail}
              onChange={(event) => setNewEmail(event.target.value)}
            />
            <div>
              <Button type="submit" disabled={pending || !newEmail.trim()}>
                {t("submit")}
              </Button>
            </div>
          </form>
        )}
        <FormError message={error} />
        <FormSuccess message={notice} />
      </div>
    </SettingsSection>
  );
}

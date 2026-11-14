"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { FormError } from "@/components/common/FormError";
import type { AppUser } from "@/lib/auth/session";
import { sendPasswordResetAction } from "../actions";
import { useActionErrorText } from "../lib/errors";
import { FormSuccess, SettingsSection } from "./SettingsSection";

/**
 * Password changes go through the reset email: the link opens a recovery session and the person
 * picks a new password on /reset-password. Google accounts have no Aura password at all.
 */
export function PasswordCard({ user }: { user: AppUser }) {
  const t = useTranslations("profile.password");
  const errorText = useActionErrorText();
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const [pending, startTransition] = useTransition();
  const applicable = user.provider === "email";

  const send = () => {
    setError(null);
    startTransition(async () => {
      const result = await sendPasswordResetAction();
      if (!result.ok) setError(errorText(result));
      else setSent(true);
    });
  };

  return (
    <SettingsSection title={t("title")} description={applicable ? t("hint") : t("managedByGoogle")}>
      {applicable && (
        <div className="flex max-w-md flex-col gap-3">
          <FormError message={error} />
          <FormSuccess message={sent ? t("sent", { email: user.email ?? "" }) : null} />
          <div>
            <Button type="button" variant="outline" onClick={send} disabled={pending || sent}>
              {t("submit")}
            </Button>
          </div>
        </div>
      )}
    </SettingsSection>
  );
}

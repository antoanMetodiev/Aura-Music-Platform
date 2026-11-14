"use client";

import { useState, useTransition } from "react";
import { KeyRound } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { FormError } from "@/components/common/FormError";
import { routes } from "@/config/routes";
import { signOut } from "@/lib/auth/client";
import { SettingsSection } from "./SettingsSection";

/** Which identity signs this account in — Supabase "identities" (google / email). */
export function SignInMethodCard({ identities }: { identities: string[] }) {
  const t = useTranslations("profile.accounts");
  const rows = identities.length ? identities : ["email"];

  return (
    <SettingsSection title={t("title")}>
      <ul className="flex max-w-md flex-col divide-y divide-border">
        {rows.map((provider, index) => (
          <li key={provider} className="flex items-center gap-3 py-3">
            {provider === "google" ? <GoogleG /> : <KeyRound className="size-4 text-muted-foreground" />}
            <span className="flex-1 text-sm">{provider === "google" ? t("google") : t("password")}</span>
            {index === 0 && <Badge variant="secondary">{t("primary")}</Badge>}
          </li>
        ))}
      </ul>
    </SettingsSection>
  );
}

/** "Sign out everywhere": revokes every session and refresh token of this account, this browser included. */
export function DevicesCard() {
  const t = useTranslations("profile.sessions");
  const profile = useTranslations("profile");
  const locale = useLocale();
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  const revokeAll = () => {
    setError(null);
    startTransition(async () => {
      try {
        await signOut(`/${locale}${routes.home}`, "global");
      } catch {
        setError(profile("errors.PROVIDER_ERROR"));
      }
    });
  };

  return (
    <SettingsSection title={t("title")} description={t("hint")}>
      <div className="flex max-w-md flex-col gap-3">
        <FormError message={error} />
        <div>
          <Button type="button" variant="outline" onClick={revokeAll} disabled={pending}>
            {t("revokeAll")}
          </Button>
        </div>
      </div>
    </SettingsSection>
  );
}

function GoogleG() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" aria-hidden="true">
      <path fill="#4285F4" d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.5h6.5c-.3 1.5-1.1 2.7-2.4 3.6v3h3.9c2.3-2.1 3.5-5.2 3.5-8.8z" />
      <path fill="#34A853" d="M12 24c3.2 0 6-1.1 8-2.9l-3.9-3c-1.1.7-2.5 1.2-4.1 1.2-3.1 0-5.8-2.1-6.7-5H1.2v3.1C3.2 21.3 7.3 24 12 24z" />
      <path fill="#FBBC05" d="M5.3 14.3c-.2-.7-.4-1.5-.4-2.3s.1-1.6.4-2.3V6.6H1.2C.4 8.2 0 10 0 12s.4 3.8 1.2 5.4l4.1-3.1z" />
      <path fill="#EA4335" d="M12 4.8c1.8 0 3.3.6 4.6 1.8l3.4-3.4C18 1.2 15.2 0 12 0 7.3 0 3.2 2.7 1.2 6.6l4.1 3.1c.9-2.9 3.6-4.9 6.7-4.9z" />
    </svg>
  );
}

"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { routes } from "@/config/routes";
import { signIn } from "@/lib/auth/client";
import { FormError } from "./FormError";

/** Social sign-in. One provider today (Google); the divider reads "or" against the email form below. */
export function OAuthButtons() {
  const t = useTranslations("auth");
  const locale = useLocale();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const google = async () => {
    setPending(true);
    setError(null);
    // Redirects to Google; on success Better Auth sends the browser to callbackURL with the cookie set.
    const { error } = await signIn.social({
      provider: "google",
      callbackURL: `/${locale}${routes.home}`,
      errorCallbackURL: `/${locale}${routes.login}?error=oauth`,
    });
    if (error) {
      setError(t("errors.generic"));
      setPending(false);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <Button type="button" variant="outline" size="lg" className="h-10 w-full gap-2.5" onClick={google} disabled={pending}>
        <GoogleMark />
        {t("oauth.google")}
      </Button>
      <FormError message={error} />
      <div className="flex items-center gap-3 text-xs tracking-[0.14em] text-subtle-foreground uppercase">
        <span className="h-px flex-1 bg-border" />
        {t("oauth.or")}
        <span className="h-px flex-1 bg-border" />
      </div>
    </div>
  );
}

function GoogleMark() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" aria-hidden="true">
      <path fill="#4285F4" d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.5h6.5c-.3 1.5-1.1 2.7-2.4 3.6v3h3.9c2.3-2.1 3.5-5.2 3.5-8.8z" />
      <path fill="#34A853" d="M12 24c3.2 0 6-1.1 8-2.9l-3.9-3c-1.1.7-2.5 1.2-4.1 1.2-3.1 0-5.8-2.1-6.7-5H1.2v3.1C3.2 21.3 7.3 24 12 24z" />
      <path fill="#FBBC05" d="M5.3 14.3c-.2-.7-.4-1.5-.4-2.3s.1-1.6.4-2.3V6.6H1.2C.4 8.2 0 10 0 12s.4 3.8 1.2 5.4l4.1-3.1z" />
      <path fill="#EA4335" d="M12 4.8c1.8 0 3.3.6 4.6 1.8l3.4-3.4C18 1.2 15.2 0 12 0 7.3 0 3.2 2.7 1.2 6.6l4.1 3.1c.9-2.9 3.6-4.9 6.7-4.9z" />
    </svg>
  );
}

"use client";

import { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { Button } from "@/components/ui/button";
import { FormError } from "@/components/common/FormError";
import { FormField } from "@/components/common/FormField";
import type { AppUser } from "@/lib/auth/session";
import { normalizeUsername } from "@/lib/auth/username";
import { updateDetailsAction } from "../actions";
import { useActionErrorText } from "../lib/errors";
import { FormSuccess, SettingsSection } from "./SettingsSection";

/** Display name + username. A username change moves the page to the new /profile/<username>. */
export function DetailsForm({ user }: { user: AppUser }) {
  const t = useTranslations("profile");
  const errorText = useActionErrorText();
  const router = useRouter();
  const [name, setName] = useState(user.name ?? "");
  const [username, setUsername] = useState(user.username);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [pending, startTransition] = useTransition();

  const dirty = name.trim() !== (user.name ?? "") || username.trim() !== user.username;

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setSaved(false);
    startTransition(async () => {
      const result = await updateDetailsAction({ name, username });
      if (!result.ok) {
        setError(errorText(result));
        return;
      }
      setSaved(true);
      const finalUsername = normalizeUsername(username);
      if (finalUsername !== user.username) router.replace(routes.profile(finalUsername));
      router.refresh();
    });
  };

  return (
    <SettingsSection title={t("details.title")}>
      <form onSubmit={submit} noValidate className="flex max-w-md flex-col gap-4">
        <FormField
          label={t("details.displayName")}
          autoComplete="name"
          value={name}
          maxLength={60}
          onChange={(event) => setName(event.target.value)}
        />
        <FormField
          label={t("details.username")}
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          value={username}
          maxLength={30}
          hint={t("details.usernameHint", { username: username || "…" })}
          onChange={(event) => setUsername(event.target.value)}
        />
        <FormError message={error} />
        <FormSuccess message={saved ? t("saved") : null} />
        <div>
          <Button type="submit" disabled={pending || !dirty}>
            {t("save")}
          </Button>
        </div>
      </form>
    </SettingsSection>
  );
}

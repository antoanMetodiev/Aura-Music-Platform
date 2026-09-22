"use client";

import { useState, useTransition } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { FormError } from "@/components/common/FormError";
import { routes } from "@/config/routes";
import { signOut } from "@/lib/auth/client";
import { deleteAccountAction } from "../actions";
import { useActionErrorText } from "../lib/errors";
import { SettingsSection } from "./SettingsSection";

/** Delete the Supabase user (and our data about them) behind a confirm dialog, then clear the cookie. */
export function DeleteAccount({ username }: { username: string }) {
  const t = useTranslations("profile.danger");
  const profile = useTranslations("profile");
  const errorText = useActionErrorText();
  const locale = useLocale();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  const confirm = () => {
    setError(null);
    startTransition(async () => {
      const result = await deleteAccountAction();
      if (!result.ok) {
        setError(errorText(result));
        return;
      }
      await signOut(`/${locale}${routes.home}`);
    });
  };

  return (
    <SettingsSection title={t("title")} description={t("hint")} destructive>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger render={<Button variant="destructive" />}>{t("action")}</DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("confirmTitle")}</DialogTitle>
            <DialogDescription>{t("confirmBody", { username })}</DialogDescription>
          </DialogHeader>
          <FormError message={error} />
          <DialogFooter>
            <Button variant="ghost" onClick={() => setOpen(false)} disabled={pending}>
              {profile("cancel")}
            </Button>
            <Button variant="destructive" onClick={confirm} disabled={pending}>
              {t("confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </SettingsSection>
  );
}

"use client";

import { useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { Button } from "@/components/ui/button";
import { FormError } from "@/components/common/FormError";
import { UserAvatar } from "@/components/common/UserAvatar";
import type { AppUser } from "@/lib/auth/session";
import { SettingsSection } from "./SettingsSection";

type UploadErrorCode = "UNSUPPORTED_TYPE" | "TOO_LARGE" | "UNREADABLE_IMAGE";

/** Upload / replace / remove the profile photo via /api/profile/avatar. */
export function AvatarEditor({ user }: { user: AppUser }) {
  const t = useTranslations("profile.avatar");
  const router = useRouter();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Optimistic preview while the upload is in flight / before the server re-render lands.
  const [preview, setPreview] = useState<string | null | undefined>(undefined);

  const avatarUrl = preview === undefined ? user.avatarUrl : (preview ?? user.picture ?? undefined);

  const upload = async (file: File) => {
    setBusy(true);
    setError(null);
    const localUrl = URL.createObjectURL(file);
    setPreview(localUrl);
    try {
      const body = new FormData();
      body.set("file", file);
      const response = await fetch("/api/profile/avatar", { method: "POST", body });
      if (!response.ok) {
        const { code } = (await response.json().catch(() => ({}))) as { code?: UploadErrorCode };
        setPreview(undefined);
        setError(code && code in errorKeys ? t(`errors.${code}`) : t("errors.generic"));
        return;
      }
      const { image } = (await response.json()) as { image: string | null };
      setPreview(image);
      router.refresh();
    } catch {
      setPreview(undefined);
      setError(t("errors.generic"));
    } finally {
      URL.revokeObjectURL(localUrl);
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    setError(null);
    try {
      const response = await fetch("/api/profile/avatar", { method: "DELETE" });
      if (!response.ok) throw new Error();
      setPreview(null);
      router.refresh();
    } catch {
      setError(t("errors.generic"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <SettingsSection title={t("title")} description={t("hint")}>
      <div className="flex flex-col items-start gap-5 sm:flex-row sm:items-center">
        <UserAvatar
          user={{ displayName: user.name ?? user.username, username: user.username, avatarUrl }}
          className="size-24 [&_[data-slot=avatar-fallback]]:text-2xl"
        />
        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={input}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif,image/avif"
            className="sr-only"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) void upload(file);
              event.target.value = "";
            }}
          />
          <Button type="button" onClick={() => input.current?.click()} disabled={busy}>
            {busy ? t("uploading") : avatarUrl ? t("change") : t("upload")}
          </Button>
          {avatarUrl ? (
            <Button type="button" variant="ghost" onClick={remove} disabled={busy}>
              {t("remove")}
            </Button>
          ) : null}
        </div>
      </div>
      <div className="mt-4">
        <FormError message={error} />
      </div>
    </SettingsSection>
  );
}

const errorKeys: Record<UploadErrorCode, true> = { UNSUPPORTED_TYPE: true, TOO_LARGE: true, UNREADABLE_IMAGE: true };

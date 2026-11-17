"use client";

import { useState } from "react";
import { Ban, MoreHorizontal, UserCheck, UserPlus } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useRouter } from "@/i18n/navigation";
import { cn } from "@/lib/utils";
import { getClientAccessToken } from "@/lib/api/token";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { setBlocked, setFollowing, type SocialProfile } from "../api/socialApi";
import { FriendButton } from "./FriendButton";

/** Action row on someone else's profile: friendship, follow, and a menu with Block. */
export function ProfileSocialActions({ profile }: { profile: SocialProfile }) {
  const t = useTranslations("social");
  const router = useRouter();
  const [following, setFollowingState] = useState(profile.following);
  const [busy, setBusy] = useState(false);
  const { user } = profile;
  const blocked = profile.relationship === "BLOCKED";

  const toggleFollow = async () => {
    const next = !following;
    setFollowingState(next);
    setBusy(true);
    try {
      await setFollowing(await getClientAccessToken(), user.id, next);
      router.refresh();
    } catch {
      setFollowingState(!next);
      toast.error(t("actionFailed"));
    } finally {
      setBusy(false);
    }
  };

  const block = async () => {
    setBusy(true);
    try {
      await setBlocked(await getClientAccessToken(), user.id, true);
      toast.success(t("blockedToast", { name: user.displayName }));
      router.refresh();
    } catch {
      toast.error(t("actionFailed"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
      {/* Keyed by the server's answer so a refresh after blocking/unblocking resets it. */}
      <FriendButton key={profile.relationship} userId={user.id} displayName={user.displayName} initial={profile.relationship} />
      {!blocked ? (
        <>
          <button
            type="button"
            onClick={() => void toggleFollow()}
            disabled={busy}
            aria-pressed={following}
            className={cn(
              "inline-flex h-9 items-center gap-1.5 rounded-full border px-4 text-sm font-medium transition-colors disabled:opacity-70",
              following ? "border-primary/60 text-primary-hover hover:border-primary" : "border-border-strong text-foreground hover:border-foreground",
            )}
          >
            {following ? <UserCheck className="size-4" /> : <UserPlus className="size-4" />}
            {following ? t("following") : profile.followsYou ? t("followBack") : t("follow")}
          </button>
          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label={t("moreOptions")}
              disabled={busy}
              className="grid size-9 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-white/[0.06] hover:text-foreground"
            >
              <MoreHorizontal className="size-5" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
              <DropdownMenuItem variant="destructive" onClick={() => void block()}>
                <Ban />
                {t("block")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </>
      ) : null}
    </div>
  );
}

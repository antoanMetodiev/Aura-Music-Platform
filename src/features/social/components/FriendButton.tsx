"use client";

import { useState } from "react";
import { Check, ChevronDown, Clock, UserCheck, UserMinus, UserPlus, X } from "lucide-react";
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
import {
  acceptFriendRequest,
  removeFriendRequest,
  sendFriendRequest,
  setBlocked,
  unfriend,
  type Relationship,
} from "../api/socialApi";

interface FriendButtonProps {
  userId: string;
  displayName: string;
  initial: Relationship;
  /** Smaller pill for list rows. */
  compact?: boolean;
  /** Called with the new state after every successful change. */
  onChange?: (next: Relationship) => void;
  className?: string;
}

const pill =
  "inline-flex items-center gap-1.5 rounded-full border text-sm font-medium transition-colors disabled:opacity-70";

/**
 * The one control for where you stand with someone: Add friend → Requested (click to cancel) →
 * Friends (menu: remove); Accept / Decline when they asked you; Unblock when you blocked them.
 * Server-rendered pages around it are refreshed after a change so counts and lists follow.
 */
export function FriendButton({ userId, displayName, initial, compact, onChange, className }: FriendButtonProps) {
  const t = useTranslations("social");
  const router = useRouter();
  const [state, setState] = useState<Relationship>(initial);
  const [busy, setBusy] = useState(false);

  if (state === "SELF") return null;

  const run = async (action: (token: string | null) => Promise<Relationship>, successToast?: string) => {
    setBusy(true);
    try {
      const next = await action(await getClientAccessToken());
      setState(next);
      onChange?.(next);
      if (successToast) toast.success(successToast);
      router.refresh();
    } catch {
      toast.error(t("actionFailed"));
    } finally {
      setBusy(false);
    }
  };

  const size = compact ? "h-8 px-3" : "h-9 px-4";
  const neutral = "border-border-strong text-foreground hover:border-foreground";
  const active = "border-primary/60 text-primary-hover hover:border-primary";

  if (state === "NONE") {
    return (
      <button
        type="button"
        disabled={busy}
        onClick={() => void run((tk) => sendFriendRequest(tk, userId), t("requestSentToast", { name: displayName }))}
        className={cn(pill, size, "border-primary bg-primary text-primary-foreground hover:bg-primary-hover", className)}
      >
        <UserPlus className="size-4" />
        {t("addFriend")}
      </button>
    );
  }

  if (state === "REQUEST_SENT") {
    return (
      <button
        type="button"
        disabled={busy}
        title={t("cancelRequest")}
        onClick={() => void run(async (tk) => (await removeFriendRequest(tk, userId), "NONE"))}
        className={cn(pill, size, neutral, "group", className)}
      >
        <Clock className="size-4 group-hover:hidden" />
        <X className="hidden size-4 group-hover:block" />
        <span className="group-hover:hidden">{t("requested")}</span>
        <span className="hidden group-hover:inline">{t("cancelRequest")}</span>
      </button>
    );
  }

  if (state === "REQUEST_RECEIVED") {
    return (
      <span className={cn("inline-flex items-center gap-2", className)}>
        <button
          type="button"
          disabled={busy}
          onClick={() => void run((tk) => acceptFriendRequest(tk, userId), t("nowFriendsToast", { name: displayName }))}
          className={cn(pill, size, "border-primary bg-primary text-primary-foreground hover:bg-primary-hover")}
        >
          <Check className="size-4" />
          {t("accept")}
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => void run(async (tk) => (await removeFriendRequest(tk, userId), "NONE"))}
          className={cn(pill, size, neutral)}
        >
          {t("decline")}
        </button>
      </span>
    );
  }

  if (state === "BLOCKED") {
    return (
      <button
        type="button"
        disabled={busy}
        onClick={() => void run(async (tk) => (await setBlocked(tk, userId, false), "NONE"), t("unblockedToast", { name: displayName }))}
        className={cn(pill, size, neutral, className)}
      >
        {t("unblock")}
      </button>
    );
  }

  // FRIENDS
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        disabled={busy}
        className={cn(pill, size, active, className)}
      >
        <UserCheck className="size-4" />
        {t("friends")}
        <ChevronDown className="size-3.5 opacity-70" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem
          variant="destructive"
          onClick={() => void run(async (tk) => (await unfriend(tk, userId), "NONE"), t("unfriendedToast", { name: displayName }))}
        >
          <UserMinus />
          {t("removeFriend")}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

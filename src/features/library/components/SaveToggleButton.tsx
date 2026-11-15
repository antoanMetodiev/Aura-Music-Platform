"use client";

import { useEffect, useState } from "react";
import { Check, Plus, UserCheck, UserPlus } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";
import { getClientAccessToken } from "@/lib/api/token";
import { savedContains, setSaved, type SavedCollection } from "../api/libraryApi";
import { useLibraryStore } from "../store/library-store";

interface SaveToggleButtonProps {
  /** "saved-albums" → Save / Saved; "saved-artists" → Follow / Following. */
  collection: SavedCollection;
  id: string;
  className?: string;
}

/** One album or artist page has exactly one of these, so it asks for its own state — no batching needed. */
export function SaveToggleButton({ collection, id, className }: SaveToggleButtonProps) {
  const t = useTranslations("library");
  const router = useRouter();
  const signedIn = useLibraryStore((s) => s.signedIn);
  const [saved, setSavedState] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!signedIn) return;
    let cancelled = false;
    (async () => {
      try {
        const answer = await savedContains(await getClientAccessToken(), collection, id);
        if (!cancelled) setSavedState(answer);
      } catch {
        // Library unreachable: the button stays in its neutral "not saved" look.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [signedIn, collection, id]);

  const artist = collection === "saved-artists";
  const isSaved = saved === true;

  const onClick = async () => {
    if (!signedIn) {
      router.push(routes.login);
      return;
    }
    const next = !isSaved;
    setSavedState(next);
    setBusy(true);
    try {
      await setSaved(await getClientAccessToken(), collection, id, next);
      toast.success(artist ? (next ? t("followedToast") : t("unfollowedToast")) : next ? t("albumSavedToast") : t("albumRemovedToast"));
    } catch {
      setSavedState(!next);
      toast.error(t("saveFailed"));
    } finally {
      setBusy(false);
    }
  };

  const Icon = artist ? (isSaved ? UserCheck : UserPlus) : isSaved ? Check : Plus;
  const label = artist ? (isSaved ? t("following") : t("follow")) : isSaved ? t("savedAlbum") : t("saveAlbum");

  return (
    <button
      type="button"
      onClick={() => void onClick()}
      disabled={busy}
      aria-pressed={isSaved}
      className={cn(
        "inline-flex h-9 items-center gap-1.5 rounded-full border px-4 text-sm font-medium transition-colors disabled:opacity-70",
        isSaved ? "border-primary/60 text-primary-hover hover:border-primary" : "border-border-strong text-foreground hover:border-foreground",
        className,
      )}
    >
      <Icon className="size-4" />
      {label}
    </button>
  );
}

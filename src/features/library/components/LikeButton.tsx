"use client";

import { useEffect } from "react";
import { Heart } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";
import { useLibraryStore } from "../store/library-store";

interface LikeButtonProps {
  trackId: string;
  className?: string;
  iconClassName?: string;
  /** Hide until the row is hovered — unless the track is liked, then always show it. */
  revealOnHover?: boolean;
}

/** ♥ for one track. Learns its state lazily (batched per tick), flips optimistically. */
export function LikeButton({ trackId, className, iconClassName, revealOnHover = false }: LikeButtonProps) {
  const t = useTranslations("library");
  const router = useRouter();
  const signedIn = useLibraryStore((s) => s.signedIn);
  const liked = useLibraryStore((s) => s.liked[trackId] ?? false);
  const ensureLiked = useLibraryStore((s) => s.ensureLiked);
  const toggleLike = useLibraryStore((s) => s.toggleLike);

  useEffect(() => {
    ensureLiked([trackId]);
  }, [ensureLiked, trackId, signedIn]);

  const onClick = async (event: React.MouseEvent) => {
    event.stopPropagation();
    if (!signedIn) {
      router.push(routes.login);
      return;
    }
    try {
      const now = await toggleLike(trackId);
      toast.success(now ? t("likedToast") : t("unlikedToast"));
    } catch {
      toast.error(t("likeFailed"));
    }
  };

  const label = liked ? t("removeFromLiked") : t("saveToLiked");
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={liked}
      title={label}
      onClick={onClick}
      className={cn(
        "grid size-8 shrink-0 place-items-center rounded-full transition-colors",
        liked ? "text-primary hover:text-primary-hover" : "text-muted-foreground hover:text-foreground",
        revealOnHover && !liked && "opacity-0 group-hover:opacity-100 focus-visible:opacity-100",
        className,
      )}
    >
      <Heart className={cn("size-4", liked && "fill-current", iconClassName)} />
    </button>
  );
}

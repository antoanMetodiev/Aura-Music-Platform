"use client";

import { Heart, Pause, Play, SkipForward } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { joinArtists } from "@/lib/utils/format";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { usePlayerStore } from "../store/player-store";

/**
 * Compact mobile player docked above the bottom nav. Tapping the body will open
 * the full-screen player sheet (arrives with the Player slice).
 */
export function MiniPlayer({ className }: { className?: string }) {
  const t = useTranslations("player");
  const current = usePlayerStore((s) => s.current);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const positionMs = usePlayerStore((s) => s.positionMs);
  const toggle = usePlayerStore((s) => s.toggle);
  const next = usePlayerStore((s) => s.next);

  if (!current) return null;
  const progress = Math.min(100, (positionMs / current.durationMs) * 100);

  return (
    <div className={cn("px-2 md:hidden", className)}>
      <div className="relative flex h-14 items-center gap-3 overflow-hidden rounded-lg border border-border bg-elevated pr-1 pl-2 shadow-[0_-8px_30px_-12px_rgba(0,0,0,0.8)]">
        <ArtworkImage artwork={current.artwork} alt="" seed={current.id} sizes="40px" className="size-10 shrink-0" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{current.title}</p>
          <p className="truncate text-xs text-muted-foreground">{joinArtists(current.artists)}</p>
        </div>
        <button type="button" aria-label={t("saveToLiked")} className="grid size-10 place-items-center text-muted-foreground">
          <Heart className="size-5" />
        </button>
        <button type="button" aria-label={isPlaying ? t("pause") : t("play")} onClick={toggle} className="grid size-10 place-items-center">
          {isPlaying ? <Pause className="size-5 fill-current" /> : <Play className="size-5 translate-x-px fill-current" />}
        </button>
        <button type="button" aria-label={t("next")} onClick={next} className="grid size-10 place-items-center text-muted-foreground">
          <SkipForward className="size-5 fill-current" />
        </button>
        <span className="absolute inset-x-0 bottom-0 h-0.5 bg-border-strong">
          <span className="block h-full bg-primary transition-[width] duration-200" style={{ width: `${progress}%` }} />
        </span>
      </div>
    </div>
  );
}

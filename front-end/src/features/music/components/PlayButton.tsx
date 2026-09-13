"use client";

import { Pause, Play } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { usePlayerStore } from "@/features/player/store/player-store";
import type { Track } from "@/types/catalog";

interface PlayButtonProps {
  /** Tracks to play; the first becomes current, the rest the queue. */
  tracks: Track[];
  /** Context id (album / playlist / track) — used to show "playing" state for this item. */
  contextId?: string;
  size?: "sm" | "md" | "lg";
  className?: string;
  label?: string;
}

/**
 * Accent-blue round play button used on cards, heroes and rows.
 * Shows pause when this exact context is already playing.
 */
export function PlayButton({ tracks, contextId, size = "md", className, label }: PlayButtonProps) {
  const t = useTranslations("common");
  const current = usePlayerStore((s) => s.current);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const play = usePlayerStore((s) => s.play);
  const pause = usePlayerStore((s) => s.pause);

  const first = tracks[0];
  const thisContextActive =
    !!current && (contextId ? tracks.some((t) => t.id === current.id) : current.id === first?.id);
  const showPause = thisContextActive && isPlaying;

  const onClick = (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    if (!first) return;
    if (showPause) return pause();
    if (thisContextActive) return play();
    play(first, tracks);
  };

  return (
    <button
      type="button"
      aria-label={label ?? (showPause ? t("pause") : t("play"))}
      onClick={onClick}
      disabled={!first}
      className={cn(
        "grid shrink-0 place-items-center rounded-full bg-primary text-primary-foreground shadow-[0_8px_24px_-8px_var(--primary)] transition-all",
        "hover:scale-105 hover:bg-primary-hover active:scale-95 disabled:opacity-40",
        size === "sm" && "size-9",
        size === "md" && "size-11",
        size === "lg" && "size-14",
        className,
      )}
    >
      {showPause ? (
        <Pause className={cn("fill-current", size === "lg" ? "size-6" : "size-[18px]")} />
      ) : (
        <Play className={cn("translate-x-px fill-current", size === "lg" ? "size-6" : "size-[18px]")} />
      )}
    </button>
  );
}

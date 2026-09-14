"use client";

import { Play } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";
import { formatDuration } from "@/lib/utils/format";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { EqualizerBars } from "@/features/player/components/EqualizerBars";
import { usePlayerStore } from "@/features/player/store/player-store";
import type { Track } from "@/types/catalog";

interface TrackRowProps {
  index: number;
  track: Track;
  /** Full tracklist this row belongs to — becomes the queue when this row starts playing. */
  context: Track[];
  /** "md" = slightly larger artwork and type, for short lists sat next to a tall card. */
  size?: "sm" | "md";
  className?: string;
}

/** Compact track row for lists without a table header (search results; album/playlist pages later). */
export function TrackRow({ index, track, context, size = "sm", className }: TrackRowProps) {
  const t = useTranslations("common");
  const current = usePlayerStore((s) => s.current);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const play = usePlayerStore((s) => s.play);
  const active = current?.id === track.id;

  return (
    <li
      className={cn(
        "group grid items-center gap-3 rounded-md px-3 py-2 transition-colors hover:bg-hover",
        size === "md" ? "grid-cols-[1.5rem_3rem_1fr_auto]" : "grid-cols-[1.5rem_2.5rem_1fr_auto]",
        active && "bg-active/50",
        className,
      )}
    >
      <button
        type="button"
        aria-label={t("playItem", { title: track.title })}
        onClick={() => play(track, context)}
        className="grid size-6 place-items-center font-mono text-sm text-subtle-foreground"
      >
        {active ? (
          <EqualizerBars playing={isPlaying} />
        ) : (
          <>
            <span className="group-hover:hidden">{index + 1}</span>
            <Play className="hidden size-3.5 fill-current text-foreground group-hover:block" />
          </>
        )}
      </button>

      <ArtworkImage artwork={track.artwork} alt="" seed={track.id} sizes={size === "md" ? "48px" : "40px"} className={size === "md" ? "size-12" : "size-10"} />

      <div className="min-w-0">
        <Link
          href={routes.track(track.id)}
          className={cn("block truncate font-medium hover:underline", size === "md" ? "text-[15px]" : "text-sm", active && "text-primary-hover")}
        >
          {track.title}
        </Link>
        <p className="truncate text-xs text-muted-foreground">
          {track.explicit && (
            <span
              className="mr-1.5 inline-grid size-3.5 place-items-center rounded-[2px] bg-muted-foreground/80 align-[-2px] text-[9px] font-bold text-background"
              aria-label={t("explicit")}
            >
              E
            </span>
          )}
          {track.artists.map((artist, i) => (
            <span key={artist.id}>
              {i > 0 && ", "}
              <Link href={routes.artist(artist.id)} className="hover:text-foreground hover:underline">
                {artist.name}
              </Link>
            </span>
          ))}
        </p>
      </div>

      <span className="font-mono text-xs tabular-nums text-subtle-foreground">{formatDuration(track.durationMs)}</span>
    </li>
  );
}

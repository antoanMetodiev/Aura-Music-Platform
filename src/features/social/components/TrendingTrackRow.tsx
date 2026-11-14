"use client";

import { Play } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";
import { formatDuration, joinArtists } from "@/lib/utils/format";
import { AvatarGroup, AvatarGroupCount } from "@/components/ui/avatar";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { UserAvatar } from "@/components/common/UserAvatar";
import { EqualizerBars } from "@/features/player/components/EqualizerBars";
import { usePlayerStore } from "@/features/player/store/player-store";
import type { Track } from "@/types/catalog";
import type { UserSummary } from "@/types/social";

interface TrendingTrackRowProps {
  index: number;
  track: Track;
  listeners: UserSummary[];
  /** All tracks in the list, so playing one queues the rest. */
  context: Track[];
}

/** Row for "Trending among friends": rank, artwork, title, stacked listener avatars. */
export function TrendingTrackRow({ index, track, listeners, context }: TrendingTrackRowProps) {
  const t = useTranslations("common");
  const current = usePlayerStore((s) => s.current);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const play = usePlayerStore((s) => s.play);
  const active = current?.id === track.id;
  const shown = listeners.slice(0, 3);
  const extra = listeners.length - shown.length;

  return (
    <li
      className={cn(
        "group grid grid-cols-[1.5rem_2.5rem_1fr_auto_auto] items-center gap-3 rounded-md px-3 py-2 transition-colors hover:bg-hover",
        active && "bg-active/50",
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

      <ArtworkImage artwork={track.artwork} alt="" seed={track.id} sizes="40px" className="size-10" />

      <div className="min-w-0">
        <Link
          href={routes.track(track.id)}
          className={cn("block truncate text-sm font-medium hover:underline", active && "text-primary-hover")}
        >
          {track.title}
        </Link>
        <p className="truncate text-xs text-muted-foreground">
          {track.explicit && (
            <span className="mr-1.5 inline-grid size-3.5 place-items-center rounded-[2px] bg-muted-foreground/80 align-[-2px] text-[9px] font-bold text-background" aria-label={t("explicit")}>
              E
            </span>
          )}
          {joinArtists(track.artists)}
        </p>
      </div>

      <AvatarGroup className="hidden -space-x-1 sm:flex">
        {shown.map((user) => (
          <UserAvatar key={user.id} user={user} size="sm" />
        ))}
        {extra > 0 && <AvatarGroupCount className="size-6 text-[10px]">+{extra}</AvatarGroupCount>}
      </AvatarGroup>

      <span className="w-10 text-right font-mono text-xs tabular-nums text-subtle-foreground">
        {formatDuration(track.durationMs)}
      </span>
    </li>
  );
}

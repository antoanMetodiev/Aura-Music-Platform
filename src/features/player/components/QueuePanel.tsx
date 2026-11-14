"use client";

import { useMemo } from "react";
import { ListMusic } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { formatDuration, joinArtists } from "@/lib/utils/format";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { EqualizerBars } from "./EqualizerBars";
import { getUpNext, usePlayerStore } from "../store/player-store";
import type { Track } from "@/types/catalog";

/** Right-panel "Queue" tab. Reordering / removal arrive with the Library slice. */
export function QueuePanel() {
  const t = useTranslations("panel");
  const current = usePlayerStore((s) => s.current);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const queue = usePlayerStore((s) => s.queue);
  const upNext = useMemo(() => getUpNext(queue, current), [queue, current]);
  const play = usePlayerStore((s) => s.play);

  if (!current && upNext.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
        <span className="grid size-14 place-items-center rounded-full bg-elevated">
          <ListMusic className="size-6 text-muted-foreground" strokeWidth={1.5} />
        </span>
        <p className="text-sm font-medium">{t("queueEmpty")}</p>
        <p className="text-xs text-muted-foreground">{t("queueEmptyHint")}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5 p-4">
      {current && (
        <section>
          <h3 className="mb-2 text-[11px] font-semibold tracking-[0.14em] uppercase text-subtle-foreground">
            {t("nowPlaying")}
          </h3>
          <QueueRow track={current} active playing={isPlaying} />
        </section>
      )}
      <section>
        <h3 className="mb-2 text-[11px] font-semibold tracking-[0.14em] uppercase text-subtle-foreground">
          {t("nextUp")}
        </h3>
        <ul className="flex flex-col">
          {upNext.map((track) => (
            <li key={track.id}>
              <QueueRow track={track} onClick={() => play(track)} />
            </li>
          ))}
          {upNext.length === 0 && (
            <li className="rounded-md border border-dashed border-border p-4 text-center text-xs text-muted-foreground">
              {t("nothingQueued")}
            </li>
          )}
        </ul>
      </section>
    </div>
  );
}

function QueueRow({
  track,
  active,
  playing,
  onClick,
}: {
  track: Track;
  active?: boolean;
  playing?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={!onClick}
      className={cn(
        "-mx-2 flex w-[calc(100%+1rem)] items-center gap-3 rounded-md p-2 text-left transition-colors",
        onClick && "hover:bg-hover",
        active && "bg-active/60",
      )}
    >
      <span className="relative shrink-0">
        <ArtworkImage artwork={track.artwork} alt="" seed={track.id} sizes="40px" className="size-10" />
        {active && (
          <span className="absolute inset-0 grid place-items-center rounded-md bg-black/50">
            <EqualizerBars playing={!!playing} />
          </span>
        )}
      </span>
      <span className="min-w-0 flex-1">
        <span className={cn("block truncate text-sm font-medium", active && "text-primary-hover")}>{track.title}</span>
        <span className="block truncate text-xs text-muted-foreground">{joinArtists(track.artists)}</span>
      </span>
      <span className="font-mono text-xs text-subtle-foreground">{formatDuration(track.durationMs)}</span>
    </button>
  );
}

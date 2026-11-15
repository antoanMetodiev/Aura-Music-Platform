"use client";

import { useEffect, useMemo } from "react";
import { useTranslations } from "next-intl";
import { formatTotalDuration } from "@/lib/utils/format";
import { PlayButton } from "@/features/music/components/PlayButton";
import { TrackRow } from "@/features/music/components/TrackRow";
import type { Track } from "@/types/catalog";
import { useLibraryStore } from "../store/library-store";

/** The liked list. A track un-hearted here drops out right away (and comes back if re-hearted). */
export function LikedTrackList({ tracks }: { tracks: Track[] }) {
  const t = useTranslations("library");
  const liked = useLibraryStore((s) => s.liked);
  const setLiked = useLibraryStore((s) => s.setLiked);

  // Everything on this page is liked by definition — no need to ask the server.
  useEffect(() => {
    for (const track of tracks) if (!(track.id in useLibraryStore.getState().liked)) setLiked(track.id, true);
  }, [tracks, setLiked]);

  const visible = useMemo(() => tracks.filter((track) => liked[track.id] !== false), [tracks, liked]);
  const totalMs = visible.reduce((sum, track) => sum + track.durationMs, 0);

  return (
    <section className="flex flex-col gap-4">
      <div className="flex items-center gap-4 px-3">
        <PlayButton tracks={visible} contextId="liked" size="lg" />
        <p className="text-sm text-muted-foreground">
          {t("songs", { count: visible.length })} · {formatTotalDuration(totalMs)}
        </p>
      </div>
      <ol className="flex flex-col">
        {visible.map((track, index) => (
          <TrackRow key={track.id} index={index} track={track} context={visible} />
        ))}
      </ol>
    </section>
  );
}

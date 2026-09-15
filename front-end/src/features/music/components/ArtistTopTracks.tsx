"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import type { Track } from "@/types/catalog";
import { PlayButton } from "./PlayButton";
import { TrackRow } from "./TrackRow";

const COLLAPSED = 5;

/** "Popular" — play-all, the first five tracks, and a Spotify-style show more / show less toggle. */
export function ArtistTopTracks({ artistId, tracks }: { artistId: string; tracks: Track[] }) {
  const t = useTranslations("artist");
  const [expanded, setExpanded] = useState(false);
  const visible = expanded ? tracks : tracks.slice(0, COLLAPSED);

  return (
    <section className="flex flex-col gap-4">
      <div className="flex items-center gap-4 px-3">
        <PlayButton tracks={tracks} contextId={artistId} size="lg" />
        <h2 className="text-2xl font-bold tracking-tight">{t("popular")}</h2>
      </div>
      <ul className="flex flex-col">
        {visible.map((track, index) => (
          <TrackRow key={track.id} index={index} track={track} context={tracks} size="md" />
        ))}
      </ul>
      {tracks.length > COLLAPSED && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="self-start px-3 text-sm font-semibold text-muted-foreground transition-colors hover:text-foreground"
        >
          {expanded ? t("showLess") : t("showMore")}
        </button>
      )}
    </section>
  );
}

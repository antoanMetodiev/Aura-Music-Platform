"use client";

import { useCallback } from "react";
import { useTranslations } from "next-intl";
import { searchTracks, type SearchOptions } from "@/features/music/api/catalogApi";
import { TrackRow } from "@/features/music/components/TrackRow";
import { useProgressiveSearch } from "../hooks/useProgressiveSearch";
import { SearchPhaseHint } from "./SearchPhaseHint";
import { SectionError } from "./SectionError";
import { TopResultAndSongsSkeleton } from "./skeletons";
import { TopResultCard, type TopResult } from "./TopResultCard";

/**
 * The "All" tab's Top Result + Songs preview. Paints local catalog matches the moment they land,
 * then swaps in the provider-backed list — independent of Albums/Artists (`useProgressiveSearch`).
 */
export function TopResultAndSongsSection({ query }: { query: string }) {
  const t = useTranslations("search");
  const fetcher = useCallback((q: string, options: SearchOptions) => searchTracks(q, 6, options), []);
  const { items: tracks, phase, error } = useProgressiveSearch(query, fetcher);

  if (phase === "loading") return <TopResultAndSongsSkeleton />;
  if (error) return <SectionError message={`${t("unavailable.description")} (${error.code})`} />;
  if (tracks.length === 0) return null;

  const topResult: TopResult = { kind: "track", track: tracks[0]!, context: tracks };
  const preview = tracks.slice(0, 4);

  return (
    <div className="flex flex-col gap-2">
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <TopResultCard result={topResult} />
        <ul className="flex flex-col justify-center">
          {preview.map((track, index) => (
            <TrackRow key={track.id} index={index} track={track} context={tracks} />
          ))}
        </ul>
      </div>
      <SearchPhaseHint phase={phase} />
    </div>
  );
}

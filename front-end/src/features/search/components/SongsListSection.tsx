"use client";

import { useCallback } from "react";
import { SearchX } from "lucide-react";
import { useTranslations } from "next-intl";
import { EmptyState } from "@/components/common/EmptyState";
import { searchTracks, type SearchOptions } from "@/features/music/api/catalogApi";
import { TrackRow } from "@/features/music/components/TrackRow";
import { useProgressiveSearch } from "../hooks/useProgressiveSearch";
import { SearchPhaseHint } from "./SearchPhaseHint";
import { SectionError } from "./SectionError";
import { TrackListSkeleton } from "./skeletons";

interface SongsListSectionProps {
  query: string;
  limit: number;
  /** Renders a "No results" panel when empty instead of nothing — for the dedicated Songs filter,
   * where this list is the entire page (as opposed to the "All" tab, where Albums/Artists might
   * still have results). */
  showEmptyState?: boolean;
}

/** Full-width "Songs" filter — local matches first, provider list when it lands (`useProgressiveSearch`). */
export function SongsListSection({ query, limit, showEmptyState }: SongsListSectionProps) {
  const t = useTranslations("search");
  const fetcher = useCallback((q: string, options: SearchOptions) => searchTracks(q, limit, options), [limit]);
  const { items: tracks, phase, error } = useProgressiveSearch(query, fetcher);

  if (phase === "loading") return <TrackListSkeleton />;
  if (error) return <SectionError message={`${t("unavailable.description")} (${error.code})`} />;

  if (tracks.length === 0) {
    return showEmptyState ? (
      <EmptyState icon={SearchX} title={t("noResults.title", { query })} description={t("noResults.description")} />
    ) : null;
  }

  return (
    <div className="flex flex-col gap-2">
      <ul className="flex flex-col">
        {tracks.map((track, index) => (
          <TrackRow key={track.id} index={index} track={track} context={tracks} />
        ))}
      </ul>
      <SearchPhaseHint phase={phase} />
    </div>
  );
}

"use client";

import { useCallback } from "react";
import { SearchX } from "lucide-react";
import { useTranslations } from "next-intl";
import { routes } from "@/config/routes";
import { EmptyState } from "@/components/common/EmptyState";
import { searchArtists, type SearchOptions } from "@/features/music/api/catalogApi";
import { HorizontalSection } from "@/features/music/components/HorizontalSection";
import { MediaCard } from "@/features/music/components/MediaCard";
import { useProgressiveSearch } from "../hooks/useProgressiveSearch";
import { SearchPhaseHint } from "./SearchPhaseHint";
import { SectionError } from "./SectionError";
import { MediaGridSkeleton, MediaRailSkeleton } from "./skeletons";

interface ArtistsSectionProps {
  query: string;
  limit: number;
  /** "rail" for the "All" tab's horizontal scroller, "grid" for the dedicated Artists filter. */
  variant: "rail" | "grid";
  /** See {@link SongsListSection}'s `showEmptyState` — only meaningful for the dedicated filter. */
  showEmptyState?: boolean;
}

/** Artists — local matches first, provider list when it lands (`useProgressiveSearch`). */
export function ArtistsSection({ query, limit, variant, showEmptyState }: ArtistsSectionProps) {
  const t = useTranslations("search");
  const home = useTranslations("home");
  const fetcher = useCallback((q: string, options: SearchOptions) => searchArtists(q, limit, options), [limit]);
  const { items: artists, phase, error } = useProgressiveSearch(query, fetcher);

  if (phase === "loading") return variant === "grid" ? <MediaGridSkeleton shape="circle" /> : <MediaRailSkeleton shape="circle" />;
  if (error) return <SectionError message={`${t("unavailable.description")} (${error.code})`} />;

  if (artists.length === 0) {
    return showEmptyState ? (
      <EmptyState icon={SearchX} title={t("noResults.title", { query })} description={t("noResults.description")} />
    ) : null;
  }

  const cards = artists.map((artist) => (
    <MediaCard
      key={artist.id}
      href={routes.artist(artist.id)}
      title={artist.name}
      subtitle={home("cards.artist")}
      artwork={artist.artwork}
      seed={artist.id}
      shape="circle"
    />
  ));

  return (
    <div className="flex flex-col gap-2">
      {variant === "grid" ? (
        <div className="flex flex-wrap gap-1">{cards}</div>
      ) : (
        <HorizontalSection title={t("filters.artists")}>{cards}</HorizontalSection>
      )}
      <SearchPhaseHint phase={phase} />
    </div>
  );
}

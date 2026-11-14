"use client";

import { useCallback } from "react";
import { SearchX } from "lucide-react";
import { useTranslations } from "next-intl";
import { routes } from "@/config/routes";
import { EmptyState } from "@/components/common/EmptyState";
import { searchAlbums, type SearchOptions } from "@/features/music/api/catalogApi";
import { HorizontalSection } from "@/features/music/components/HorizontalSection";
import { MediaCard } from "@/features/music/components/MediaCard";
import { useProgressiveSearch } from "../hooks/useProgressiveSearch";
import { SearchPhaseHint } from "./SearchPhaseHint";
import { SectionError } from "./SectionError";
import { MediaGridSkeleton, MediaRailSkeleton } from "./skeletons";

interface AlbumsSectionProps {
  query: string;
  limit: number;
  /** "rail" for the "All" tab's horizontal scroller, "grid" for the dedicated Albums filter. */
  variant: "rail" | "grid";
  /** See {@link SongsListSection}'s `showEmptyState` — only meaningful for the dedicated filter. */
  showEmptyState?: boolean;
}

/** Albums — local matches first, provider list when it lands (`useProgressiveSearch`). */
export function AlbumsSection({ query, limit, variant, showEmptyState }: AlbumsSectionProps) {
  const t = useTranslations("search");
  const fetcher = useCallback((q: string, options: SearchOptions) => searchAlbums(q, limit, options), [limit]);
  const { items: albums, phase, error } = useProgressiveSearch(query, fetcher);

  if (phase === "loading") return variant === "grid" ? <MediaGridSkeleton /> : <MediaRailSkeleton />;
  if (error) return <SectionError message={`${t("unavailable.description")} (${error.code})`} />;

  if (albums.length === 0) {
    return showEmptyState ? (
      <EmptyState icon={SearchX} title={t("noResults.title", { query })} description={t("noResults.description")} />
    ) : null;
  }

  const cards = albums.map((album) => (
    <MediaCard
      key={album.id}
      href={routes.album(album.id)}
      title={album.title}
      subtitle={album.artist.name}
      artwork={album.artwork}
      seed={album.id}
    />
  ));

  return (
    <div className="flex flex-col gap-2">
      {variant === "grid" ? (
        <div className="flex flex-wrap gap-1">{cards}</div>
      ) : (
        <HorizontalSection title={t("filters.albums")}>{cards}</HorizontalSection>
      )}
      <SearchPhaseHint phase={phase} />
    </div>
  );
}

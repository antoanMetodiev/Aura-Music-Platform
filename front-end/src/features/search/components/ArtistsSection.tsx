import { SearchX } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { routes } from "@/config/routes";
import { EmptyState } from "@/components/common/EmptyState";
import { ApiError } from "@/lib/api/client";
import { searchArtists } from "@/features/music/api/catalogApi";
import { HorizontalSection } from "@/features/music/components/HorizontalSection";
import { MediaCard } from "@/features/music/components/MediaCard";
import { SectionError } from "./SectionError";

interface ArtistsSectionProps {
  query: string;
  limit: number;
  /** "rail" for the "All" tab's horizontal scroller, "grid" for the dedicated Artists filter. */
  variant: "rail" | "grid";
  /** See {@link SongsListSection}'s `showEmptyState` — only meaningful for the dedicated filter. */
  showEmptyState?: boolean;
}

/** Independent Suspense branch — fetches only `type=artists`. */
export async function ArtistsSection({ query, limit, variant, showEmptyState }: ArtistsSectionProps) {
  const t = await getTranslations("search");
  const home = await getTranslations("home");
  let artists;
  try {
    artists = await searchArtists(query, limit);
  } catch (error) {
    return <SectionError message={error instanceof ApiError ? `${t("unavailable.description")} (${error.code})` : t("unavailable.description")} />;
  }

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

  if (variant === "grid") return <div className="flex flex-wrap gap-1">{cards}</div>;
  return <HorizontalSection title={t("filters.artists")}>{cards}</HorizontalSection>;
}

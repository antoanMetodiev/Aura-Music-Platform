import { SearchX } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { routes } from "@/config/routes";
import { EmptyState } from "@/components/common/EmptyState";
import { ApiError } from "@/lib/api/client";
import { searchAlbums } from "@/features/music/api/catalogApi";
import { HorizontalSection } from "@/features/music/components/HorizontalSection";
import { MediaCard } from "@/features/music/components/MediaCard";
import { SectionError } from "./SectionError";

interface AlbumsSectionProps {
  query: string;
  limit: number;
  /** "rail" for the "All" tab's horizontal scroller, "grid" for the dedicated Albums filter. */
  variant: "rail" | "grid";
  /** See {@link SongsListSection}'s `showEmptyState` — only meaningful for the dedicated filter. */
  showEmptyState?: boolean;
}

/** Independent Suspense branch — fetches only `type=albums`. */
export async function AlbumsSection({ query, limit, variant, showEmptyState }: AlbumsSectionProps) {
  const t = await getTranslations("search");
  let albums;
  try {
    albums = await searchAlbums(query, limit);
  } catch (error) {
    return <SectionError message={error instanceof ApiError ? `${t("unavailable.description")} (${error.code})` : t("unavailable.description")} />;
  }

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

  if (variant === "grid") return <div className="flex flex-wrap gap-1">{cards}</div>;
  return <HorizontalSection title={t("filters.albums")}>{cards}</HorizontalSection>;
}

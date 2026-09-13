import { SearchX } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { EmptyState } from "@/components/common/EmptyState";
import { ApiError } from "@/lib/api/client";
import { searchTracks } from "@/features/music/api/catalogApi";
import { TrackRow } from "@/features/music/components/TrackRow";
import { SectionError } from "./SectionError";

interface SongsListSectionProps {
  query: string;
  limit: number;
  /** Renders a "No results" panel when empty instead of nothing — for the dedicated Songs filter,
   * where this list is the entire page (as opposed to the "All" tab, where Albums/Artists might
   * still have results). */
  showEmptyState?: boolean;
}

/** Independent Suspense branch for the full-width "Songs" filter — fetches only `type=tracks`. */
export async function SongsListSection({ query, limit, showEmptyState }: SongsListSectionProps) {
  const t = await getTranslations("search");
  let tracks;
  try {
    tracks = await searchTracks(query, limit);
  } catch (error) {
    return <SectionError message={error instanceof ApiError ? `${t("unavailable.description")} (${error.code})` : t("unavailable.description")} />;
  }

  if (tracks.length === 0) {
    return showEmptyState ? (
      <EmptyState icon={SearchX} title={t("noResults.title", { query })} description={t("noResults.description")} />
    ) : null;
  }

  return (
    <ul className="flex flex-col">
      {tracks.map((track, index) => (
        <TrackRow key={track.id} index={index} track={track} context={tracks} />
      ))}
    </ul>
  );
}

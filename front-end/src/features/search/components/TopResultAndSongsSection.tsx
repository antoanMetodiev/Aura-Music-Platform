import { getTranslations } from "next-intl/server";
import { ApiError } from "@/lib/api/client";
import { searchTracks } from "@/features/music/api/catalogApi";
import { TrackRow } from "@/features/music/components/TrackRow";
import { SectionError } from "./SectionError";
import { TopResultCard, type TopResult } from "./TopResultCard";

/**
 * Independent Suspense branch for the "All" tab's Top Result + Songs preview. Fetches only
 * `type=tracks` — has no idea whether Albums/Artists are still loading, erroring, or done.
 */
export async function TopResultAndSongsSection({ query }: { query: string }) {
  const t = await getTranslations("search");
  let tracks;
  try {
    tracks = await searchTracks(query, 6);
  } catch (error) {
    return <SectionError message={error instanceof ApiError ? `${t("unavailable.description")} (${error.code})` : t("unavailable.description")} />;
  }

  if (tracks.length === 0) return null;

  const topResult: TopResult = { kind: "track", track: tracks[0]!, context: tracks };
  const preview = tracks.slice(0, 4);

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
      <TopResultCard result={topResult} />
      <ul className="flex flex-col justify-center">
        {preview.map((track, index) => (
          <TrackRow key={track.id} index={index} track={track} context={tracks} />
        ))}
      </ul>
    </div>
  );
}

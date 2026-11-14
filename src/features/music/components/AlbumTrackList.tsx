import { useTranslations } from "next-intl";
import { formatTotalDuration } from "@/lib/utils/format";
import type { Track } from "@/types/catalog";
import { PlayButton } from "./PlayButton";
import { TrackRow } from "./TrackRow";

interface AlbumTrackListProps {
  albumId: string;
  tracks: Track[];
}

/** Play-all toolbar + the album's tracks in play order, split per disc when there's more than one. */
export function AlbumTrackList({ albumId, tracks }: AlbumTrackListProps) {
  const t = useTranslations("album");
  const totalMs = tracks.reduce((sum, track) => sum + track.durationMs, 0);
  const discs = groupByVolume(tracks);

  return (
    <section className="flex flex-col gap-4">
      <div className="flex items-center gap-4 px-3">
        <PlayButton tracks={tracks} contextId={albumId} size="lg" />
        <p className="text-sm text-muted-foreground">
          {t("songs", { count: tracks.length })} · {formatTotalDuration(totalMs)}
        </p>
      </div>

      {discs.map(({ volume, items }) => (
        <div key={volume} className="flex flex-col gap-1">
          {discs.length > 1 && (
            <h2 className="px-3 pt-4 text-xs font-semibold tracking-[0.14em] text-subtle-foreground uppercase">
              {t("disc", { number: volume })}
            </h2>
          )}
          <ul className="flex flex-col">
            {items.map((track, index) => (
              <TrackRow key={track.id} index={(track.trackNumber ?? index + 1) - 1} track={track} context={tracks} />
            ))}
          </ul>
        </div>
      ))}
    </section>
  );
}

function groupByVolume(tracks: Track[]): { volume: number; items: Track[] }[] {
  const byVolume = new Map<number, Track[]>();
  for (const track of tracks) {
    const volume = track.volumeNumber ?? 1;
    const bucket = byVolume.get(volume);
    if (bucket) bucket.push(track);
    else byVolume.set(volume, [track]);
  }
  return [...byVolume.entries()].sort(([a], [b]) => a - b).map(([volume, items]) => ({ volume, items }));
}

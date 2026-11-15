import { apiFetch } from "@/lib/api/client";
import { toTrack } from "@/features/music/api/catalogMappers";
import type { TrackDto } from "@/types/api";
import type { Track } from "@/types/catalog";

/** recommendation-svc `GET /recommendations/home` — the signed-out home feed in one call. */
interface HomeResponseDto {
  sections: { key: string; tracks: { track: TrackDto; playable: boolean }[] }[];
  genres: { tag: string; artistCount: number }[];
}

export interface HomeFeed {
  /** Section key as sent by the service ("trending", "discover", …) → its playable tracks. */
  sections: { key: string; tracks: Track[] }[];
  genres: string[];
}

export async function getHomeFeed(): Promise<HomeFeed> {
  const dto = await apiFetch<HomeResponseDto>("/recommendations/home");
  return {
    sections: dto.sections
      .map((s) => ({ key: s.key, tracks: s.tracks.filter((t) => t.playable).map((t) => toTrack(t.track)) }))
      .filter((s) => s.tracks.length > 0),
    genres: dto.genres.map((g) => g.tag),
  };
}

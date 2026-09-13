/**
 * Raw shapes returned by the backend API Gateway — mirrors `catalog-svc`'s
 * `adapter/web/dto` package exactly. Nothing outside `lib/api` or a feature's own `api` folder
 * should import these directly; everywhere else uses the domain types in `types/catalog.ts`,
 * produced by the mappers next to each feature's API functions.
 */

export interface ArtworkDto {
  url: string;
  width: number;
  height: number;
}

export interface ArtistSummaryDto {
  id: string;
  name: string;
  artwork: ArtworkDto | null;
}

export interface AlbumSummaryDto {
  id: string;
  title: string;
  artist: ArtistSummaryDto | null;
  artwork: ArtworkDto | null;
  releaseYear: number | null;
}

export interface TrackDto {
  id: string;
  title: string;
  version: string | null;
  durationMs: number;
  isrc: string | null;
  explicit: boolean;
  artists: ArtistSummaryDto[];
  album: AlbumSummaryDto | null;
  artwork: ArtworkDto | null;
}

export interface AlbumDto {
  id: string;
  title: string;
  albumType: "ALBUM" | "EP" | "SINGLE" | "UNKNOWN";
  releaseDate: string | null;
  artist: ArtistSummaryDto | null;
  artwork: ArtworkDto | null;
  explicit: boolean;
  numberOfTracks: number;
}

export interface ArtistDto {
  id: string;
  name: string;
  artwork: ArtworkDto | null;
  popularity: number;
}

export interface SearchResponseDto {
  query: string;
  tracks: TrackDto[];
  albums: AlbumSummaryDto[];
  artists: ArtistSummaryDto[];
}

/** Uniform error body every backend service's 4xx/5xx returns (Project-Info.md §49). */
export interface ApiErrorBody {
  code: string;
  message: string;
  traceId: string | null;
}

/** Mirrors `playback-svc`'s `PlaybackSourceResponse` — a resolved YouTube video id, never a direct audio URL. */
export interface PlaybackSourceDto {
  trackId: string;
  provider: string;
  providerResourceId: string;
  title: string;
  channelTitle: string;
  durationMs: number;
}

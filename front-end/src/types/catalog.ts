/**
 * Frontend view of the canonical music model (Project-Info.md §13).
 * These mirror the API contracts the Music Catalog / Library services will expose.
 * Provider-specific details never leak in here.
 */

export interface Artwork {
  url: string;
  /** Optional dominant color (hex) used for hero gradients. */
  dominantColor?: string;
}

export interface ArtistSummary {
  id: string;
  name: string;
  artwork?: Artwork;
}

export interface AlbumSummary {
  id: string;
  title: string;
  artist: ArtistSummary;
  artwork?: Artwork;
  releaseYear?: number;
}

export interface Track {
  id: string;
  title: string;
  durationMs: number;
  explicit: boolean;
  artists: ArtistSummary[];
  album: AlbumSummary;
  artwork?: Artwork;
  /** False when the Playback Resolver has no trusted source yet. */
  playbackAvailable: boolean;
}

export type PlaylistKind = "user" | "editorial" | "mix";

export interface PlaylistSummary {
  id: string;
  title: string;
  description?: string;
  artwork?: Artwork;
  owner: { id: string; displayName: string };
  trackCount: number;
  kind: PlaylistKind;
}

/** What the player receives — never the matching internals. (Project-Info.md §39) */
export interface PlaybackSource {
  trackId: string;
  provider: "youtube";
  providerResourceId: string;
  playbackType: "embed";
}

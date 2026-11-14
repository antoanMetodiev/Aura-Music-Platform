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

export interface Artist extends ArtistSummary {
  /** 0..1 as reported by the metadata provider. */
  popularity: number;
}

export type AlbumType = "ALBUM" | "EP" | "SINGLE" | "UNKNOWN";

export interface Album extends AlbumSummary {
  albumType: AlbumType;
  /** ISO date (YYYY-MM-DD) when known. */
  releaseDate?: string;
  explicit: boolean;
  numberOfTracks: number;
}

export interface Track {
  id: string;
  title: string;
  durationMs: number;
  explicit: boolean;
  artists: ArtistSummary[];
  album: AlbumSummary;
  artwork?: Artwork;
  /** Position inside the album — only set once the album's tracklist has been synced. */
  volumeNumber?: number;
  trackNumber?: number;
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

/** Biography, tags, similar artists, stats and outside links for an artist page's "About" (Last.fm + Discogs). */
export interface ArtistAbout {
  artistId: string;
  biography: ArtistBiography | null;
  listeners: number | null;
  playcount: number | null;
  tags: string[];
  similar: SimilarArtist[];
  links: ExternalLink[];
}

export interface ArtistBiography {
  text: string;
  /** Who wrote it — shown as a credit with a link (Last.fm text is CC BY-SA). */
  source: "LASTFM" | "DISCOGS" | string;
  url: string | null;
  language: string | null;
}

/** As the provider names them; `artist` is set when we have that artist in our own catalog. */
export interface SimilarArtist {
  name: string;
  artist: ArtistSummary | null;
}

/** `type` is a normalized host label (instagram, youtube, website, ...) mapped to an icon. */
export interface ExternalLink {
  type: string;
  url: string;
}

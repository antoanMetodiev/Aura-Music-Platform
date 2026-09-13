/**
 * Backend DTOs (`types/api.ts`) → the domain shapes every music component already renders
 * (`types/catalog.ts`, the same shapes the mock catalog uses). Keeping this conversion in one
 * place means swapping mock data for real data never touches a single component.
 *
 * Only covers what Search needs (Track / AlbumSummary / ArtistSummary) for now — full `Album` and
 * `Artist` detail mappers arrive with the Artist/Album pages, once those domain types exist.
 */
import type { AlbumSummaryDto, ArtistSummaryDto, ArtworkDto, TrackDto } from "@/types/api";
import type { AlbumSummary, ArtistSummary, Artwork, Track } from "@/types/catalog";

const UNKNOWN_ARTIST: ArtistSummary = { id: "unknown-artist", name: "Unknown Artist" };
const UNKNOWN_ALBUM: AlbumSummary = { id: "unknown-album", title: "Unknown Album", artist: UNKNOWN_ARTIST };

function toArtwork(dto: ArtworkDto | null | undefined): Artwork | undefined {
  return dto ? { url: dto.url } : undefined;
}

export function toArtistSummary(dto: ArtistSummaryDto): ArtistSummary {
  return { id: dto.id, name: dto.name, artwork: toArtwork(dto.artwork) };
}

export function toAlbumSummary(dto: AlbumSummaryDto): AlbumSummary {
  return {
    id: dto.id,
    title: dto.title,
    artist: dto.artist ? toArtistSummary(dto.artist) : UNKNOWN_ARTIST,
    artwork: toArtwork(dto.artwork),
    releaseYear: dto.releaseYear ?? undefined,
  };
}

export function toTrack(dto: TrackDto): Track {
  return {
    id: dto.id,
    title: dto.title,
    durationMs: dto.durationMs,
    explicit: dto.explicit,
    artists: dto.artists.length > 0 ? dto.artists.map(toArtistSummary) : [UNKNOWN_ARTIST],
    album: dto.album ? toAlbumSummary(dto.album) : UNKNOWN_ALBUM,
    artwork: toArtwork(dto.artwork ?? dto.album?.artwork),
    // TEMPORARY: the Playback Resolver Service doesn't exist yet, so every catalog track is
    // reported playable. Once it does, this comes from a real lookup (Project-Info.md §16).
    playbackAvailable: true,
  };
}

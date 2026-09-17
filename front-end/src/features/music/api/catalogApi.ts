import { apiFetch } from "@/lib/api/client";
import type { AlbumDto, ArtistAboutDto, ArtistDto, DiscographyStatusDto, SearchResponseDto, TrackDto } from "@/types/api";
import type { Album, AlbumSummary, Artist, ArtistAbout, ArtistSummary, Track } from "@/types/catalog";
import { toAlbum, toAlbumSummary, toArtist, toArtistAbout, toArtistSummary, toTrack } from "./catalogMappers";

export type CatalogSearchType = "tracks" | "albums" | "artists";

export interface SearchOptions {
  /**
   * `true` answers only from our own catalog — instant, never a provider call. The search page
   * fires this alongside the default call and paints whichever lands first (`useProgressiveSearch`).
   */
  localOnly?: boolean;
  signal?: AbortSignal;
}

/**
 * One search call per type rather than one combined call — `catalog-svc` itself already only
 * queries the type(s) actually requested, and issuing them separately lets each section of the
 * Search page fetch independently instead of all waiting on the slowest one.
 */
async function searchByType(query: string, type: CatalogSearchType, limit: number, options: SearchOptions): Promise<SearchResponseDto> {
  return apiFetch<SearchResponseDto>("/catalog/search", {
    searchParams: { q: query, type, limit, source: options.localOnly ? "local" : undefined },
    signal: options.signal,
  });
}

export async function searchTracks(query: string, limit = 10, options: SearchOptions = {}): Promise<Track[]> {
  const dto = await searchByType(query, "tracks", limit, options);
  return dto.tracks.map(toTrack);
}

export async function searchAlbums(query: string, limit = 10, options: SearchOptions = {}): Promise<AlbumSummary[]> {
  const dto = await searchByType(query, "albums", limit, options);
  return dto.albums.map(toAlbumSummary);
}

export async function searchArtists(query: string, limit = 10, options: SearchOptions = {}): Promise<ArtistSummary[]> {
  const dto = await searchByType(query, "artists", limit, options);
  return dto.artists.map(toArtistSummary);
}

export interface Suggestions {
  tracks: Track[];
  artists: ArtistSummary[];
}

/** Type-ahead: our own catalog only, prefix matches first, one entry per recording. Fires on every keystroke. */
export async function suggest(query: string, signal?: AbortSignal): Promise<Suggestions> {
  const dto = await apiFetch<SearchResponseDto>("/catalog/suggest", { searchParams: { q: query, limit: 5 }, signal });
  return { tracks: dto.tracks.map(toTrack), artists: dto.artists.map(toArtistSummary) };
}

/** Local read — instant. Throws an `ApiError` with status 404 for an unknown id. */
export async function getArtist(id: string): Promise<Artist> {
  return toArtist(await apiFetch<ArtistDto>(`/catalog/artists/${encodeURIComponent(id)}`));
}

/** Most popular first, one entry per recording. First open of an artist may pull their discography (a few seconds). */
export async function getArtistTopTracks(id: string, limit = 10): Promise<Track[]> {
  const dtos = await apiFetch<TrackDto[]>(`/catalog/artists/${encodeURIComponent(id)}/top-tracks`, { searchParams: { limit } });
  return dtos.map(toTrack);
}

/** Albums, EPs and singles credited to the artist, newest first. */
export async function getArtistAlbums(id: string): Promise<Album[]> {
  const dtos = await apiFetch<AlbumDto[]>(`/catalog/artists/${encodeURIComponent(id)}/albums`);
  return dtos.map(toAlbum);
}

/** Local read — instant. Throws an `ApiError` with status 404 for an unknown id. */
export async function getAlbum(id: string): Promise<Album> {
  return toAlbum(await apiFetch<AlbumDto>(`/catalog/albums/${encodeURIComponent(id)}`));
}

/**
 * In play order. The first open of an album pulls its full tracklist from the provider (a few
 * seconds); every open after that is served from our own catalog.
 */
export async function getAlbumTracks(id: string): Promise<Track[]> {
  const dtos = await apiFetch<TrackDto[]>(`/catalog/albums/${encodeURIComponent(id)}/tracks`);
  return dtos.map(toTrack);
}

/**
 * Biography, tags, similar artists, stats and outside links (Last.fm + Discogs, gathered lazily by
 * the backend on first request). `lang` picks the biography language when available.
 */
export async function getArtistAbout(id: string, lang?: string): Promise<ArtistAbout> {
  const query = lang ? `?lang=${encodeURIComponent(lang)}` : "";
  const dto = await apiFetch<ArtistAboutDto>(`/catalog/artists/${id}/about${query}`);
  return toArtistAbout(dto);
}

/**
 * Whether the backend is still filling in this artist's catalogue. Cheap, local read — the artist
 * page polls it while the discography worker does its round (see `CatalogSyncNotice`).
 */
export async function getArtistDiscographyStatus(id: string, signal?: AbortSignal): Promise<DiscographyStatusDto> {
  return apiFetch<DiscographyStatusDto>(`/catalog/artists/${encodeURIComponent(id)}/discography-status`, { signal });
}

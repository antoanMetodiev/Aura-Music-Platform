import { apiFetch } from "@/lib/api/client";
import type { AlbumDto, SearchResponseDto, TrackDto } from "@/types/api";
import type { Album, AlbumSummary, ArtistSummary, Track } from "@/types/catalog";
import { toAlbum, toAlbumSummary, toArtistSummary, toTrack } from "./catalogMappers";

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

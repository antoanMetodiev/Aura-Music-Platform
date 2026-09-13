import { apiFetch } from "@/lib/api/client";
import type { SearchResponseDto } from "@/types/api";
import type { AlbumSummary, ArtistSummary, Track } from "@/types/catalog";
import { toAlbumSummary, toArtistSummary, toTrack } from "./catalogMappers";

export type CatalogSearchType = "tracks" | "albums" | "artists";

/**
 * One search call per type rather than one combined call — `catalog-svc` itself already only
 * queries the type(s) actually requested, and issuing them separately lets each section of the
 * Search page fetch (and stream in via Suspense) independently instead of all waiting on the
 * slowest one. See `search/[query]/page.tsx`.
 */
async function searchByType(query: string, type: CatalogSearchType, limit: number): Promise<SearchResponseDto> {
  return apiFetch<SearchResponseDto>("/catalog/search", {
    searchParams: { q: query, type, limit },
  });
}

export async function searchTracks(query: string, limit = 10): Promise<Track[]> {
  const dto = await searchByType(query, "tracks", limit);
  return dto.tracks.map(toTrack);
}

export async function searchAlbums(query: string, limit = 10): Promise<AlbumSummary[]> {
  const dto = await searchByType(query, "albums", limit);
  return dto.albums.map(toAlbumSummary);
}

export async function searchArtists(query: string, limit = 10): Promise<ArtistSummary[]> {
  const dto = await searchByType(query, "artists", limit);
  return dto.artists.map(toArtistSummary);
}

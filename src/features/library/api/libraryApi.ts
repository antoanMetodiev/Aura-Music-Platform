import { apiFetch } from "@/lib/api/client";
import { toAlbum, toArtist, toTrack } from "@/features/music/api/catalogMappers";
import type { AlbumDto, ArtistDto, ArtworkDto, TrackDto } from "@/types/api";
import type { Album, Artist, PlaylistSummary, Track } from "@/types/catalog";

/**
 * library-svc (`/library/**`). Every call is user-scoped: client code passes
 * `await getClientAccessToken()`, server components `await getAccessToken()`.
 */

interface PlaylistDto {
  id: string;
  ownerId: string;
  title: string;
  description: string | null;
  isPublic: boolean;
  trackCount: number;
  artwork: ArtworkDto | null;
  createdAt: string;
  updatedAt: string;
}

interface PlaylistDetailDto {
  playlist: PlaylistDto;
  items: { id: string; position: number; addedAt: string; track: TrackDto }[];
}

interface SavedPageDto<T> {
  items: { item: T; savedAt: string }[];
  total: number;
  limit: number;
  offset: number;
}

export interface LibraryPlaylist extends PlaylistSummary {
  isPublic: boolean;
}

export interface PlaylistEntry {
  /** The item's own id — the same track can be in a playlist twice. */
  itemId: string;
  addedAt: string;
  track: Track;
}

export interface PlaylistDetail {
  playlist: LibraryPlaylist;
  entries: PlaylistEntry[];
}

export interface LikedTracksPage {
  tracks: { track: Track; likedAt: string }[];
  total: number;
}

/** The owner's display name isn't library-svc's to know — pages that show it pass it in. */
function toPlaylist(dto: PlaylistDto, ownerName = ""): LibraryPlaylist {
  return {
    id: dto.id,
    title: dto.title,
    description: dto.description ?? undefined,
    artwork: dto.artwork ?? undefined,
    owner: { id: dto.ownerId, displayName: ownerName },
    trackCount: dto.trackCount,
    kind: "user",
    isPublic: dto.isPublic,
  };
}

// ── Liked tracks ─────────────────────────────────────────────────────────────────────────

export async function getLikedTracks(token: string | null, limit = 100, offset = 0): Promise<LikedTracksPage> {
  const dto = await apiFetch<SavedPageDto<TrackDto>>("/library/liked-tracks", { token, searchParams: { limit, offset } });
  return { tracks: dto.items.map((i) => ({ track: toTrack(i.item), likedAt: i.savedAt })), total: dto.total };
}

/** Same order as `ids`. At most 200 ids per call. */
export async function likedContains(token: string | null, ids: string[]): Promise<boolean[]> {
  return apiFetch<boolean[]>("/library/liked-tracks/contains", { token, searchParams: { ids: ids.join(",") } });
}

export async function likeTrack(token: string | null, trackId: string): Promise<void> {
  await apiFetch<void>(`/library/liked-tracks/${trackId}`, { token, method: "PUT" });
}

export async function unlikeTrack(token: string | null, trackId: string): Promise<void> {
  await apiFetch<void>(`/library/liked-tracks/${trackId}`, { token, method: "DELETE" });
}

// ── Playlists ────────────────────────────────────────────────────────────────────────────

export async function getMyPlaylists(token: string | null, ownerName?: string): Promise<LibraryPlaylist[]> {
  const dtos = await apiFetch<PlaylistDto[]>("/library/playlists", { token });
  return dtos.map((d) => toPlaylist(d, ownerName));
}

export async function getPlaylist(token: string | null, id: string): Promise<PlaylistDetail> {
  const dto = await apiFetch<PlaylistDetailDto>(`/library/playlists/${id}`, { token });
  return {
    playlist: toPlaylist(dto.playlist),
    entries: dto.items.map((i) => ({ itemId: i.id, addedAt: i.addedAt, track: toTrack(i.track) })),
  };
}

export async function createPlaylist(token: string | null, title: string): Promise<LibraryPlaylist> {
  return toPlaylist(await apiFetch<PlaylistDto>("/library/playlists", { token, method: "POST", body: { title } }));
}

export async function updatePlaylist(
  token: string | null,
  id: string,
  changes: { title?: string; description?: string; isPublic?: boolean },
): Promise<LibraryPlaylist> {
  return toPlaylist(await apiFetch<PlaylistDto>(`/library/playlists/${id}`, { token, method: "PATCH", body: changes }));
}

export async function deletePlaylist(token: string | null, id: string): Promise<void> {
  await apiFetch<void>(`/library/playlists/${id}`, { token, method: "DELETE" });
}

export async function addTracksToPlaylist(token: string | null, id: string, trackIds: string[]): Promise<void> {
  await apiFetch<unknown>(`/library/playlists/${id}/items`, { token, method: "POST", body: { trackIds } });
}

export async function removePlaylistItem(token: string | null, id: string, itemId: string): Promise<void> {
  await apiFetch<void>(`/library/playlists/${id}/items/${itemId}`, { token, method: "DELETE" });
}

// ── Saved albums / followed artists ──────────────────────────────────────────────────────

export type SavedCollection = "saved-albums" | "saved-artists";

export async function savedContains(token: string | null, collection: SavedCollection, id: string): Promise<boolean> {
  const [saved] = await apiFetch<boolean[]>(`/library/${collection}/contains`, { token, searchParams: { ids: id } });
  return saved ?? false;
}

export async function setSaved(token: string | null, collection: SavedCollection, id: string, saved: boolean): Promise<void> {
  await apiFetch<void>(`/library/${collection}/${id}`, { token, method: saved ? "PUT" : "DELETE" });
}

export async function getSavedAlbums(token: string | null, limit = 50): Promise<Album[]> {
  const dto = await apiFetch<SavedPageDto<AlbumDto>>("/library/saved-albums", { token, searchParams: { limit } });
  return dto.items.map((i) => toAlbum(i.item));
}

export async function getFollowedArtists(token: string | null, limit = 50): Promise<Artist[]> {
  const dto = await apiFetch<SavedPageDto<ArtistDto>>("/library/saved-artists", { token, searchParams: { limit } });
  return dto.items.map((i) => toArtist(i.item));
}

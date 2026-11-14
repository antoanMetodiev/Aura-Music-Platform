import { apiFetch, ApiError } from "@/lib/api/client";
import type { PlaybackSourceDto } from "@/types/api";

/**
 * Resolves a catalog track to a playable YouTube video id (Project-Info.md §16). `null` means
 * "no confident source" or "track not found" — both are expected, non-exceptional outcomes here
 * (§18: playback unavailable is a normal result, not an error) — anything else (network, 5xx,
 * upstream provider down) still throws so the caller can tell the two apart.
 */
export async function resolvePlaybackSource(trackId: string): Promise<PlaybackSourceDto | null> {
  try {
    return await apiFetch<PlaybackSourceDto>(`/playback/tracks/${trackId}/source`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

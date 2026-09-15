import { apiFetch, ApiError } from "@/lib/api/client";
import type { LyricsDto } from "@/types/api";

/** One lookup per track per session — the backend caches too, but a re-open shouldn't even round-trip. */
const cache = new Map<string, Promise<LyricsDto | null>>();

/**
 * Lyrics for a track (todo.md §3.1). `null` = no provider has text for it (404 `LYRICS_NOT_FOUND`) —
 * an expected outcome, like an unresolvable playback source. Anything else (network, provider
 * outage) throws so the screen can say "try again later" instead of "no lyrics".
 */
export function getLyrics(trackId: string): Promise<LyricsDto | null> {
  const hit = cache.get(trackId);
  if (hit) return hit;

  const request = apiFetch<LyricsDto>(`/catalog/tracks/${trackId}/lyrics`).catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 404) return null;
    // Don't remember failures — the next open retries.
    cache.delete(trackId);
    throw error;
  });
  cache.set(trackId, request);
  return request;
}

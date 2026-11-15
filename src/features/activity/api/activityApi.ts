import { apiFetch } from "@/lib/api/client";
import { toTrack } from "@/features/music/api/catalogMappers";
import type { TrackDto } from "@/types/api";
import type { Track } from "@/types/catalog";

/** activity-notification-svc (`/activity/**`): listening history. Always user-scoped. */

/** One finished play, in the event envelope of Project-Info.md §26. */
export interface ListenEvent {
  eventId: string;
  eventVersion: 1;
  trackId: string;
  sessionId: string;
  startedAt: string;
  endedAt: string;
  /** Time actually heard — pauses and buffering don't count. */
  durationPlayedMs: number;
  completed: boolean;
  source: "web";
}

export interface RecentlyPlayed {
  track: Track;
  playedAt: string;
}

export async function getRecentlyPlayed(token: string | null, limit = 50): Promise<RecentlyPlayed[]> {
  const dtos = await apiFetch<{ track: TrackDto; playedAt: string }[]>("/activity/recently-played", {
    token,
    searchParams: { limit },
  });
  return dtos.map((d) => ({ track: toTrack(d.track), playedAt: d.playedAt }));
}

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

/**
 * Sent with `keepalive` so a flush started while the tab is closing still reaches the server. Not
 * through `apiFetch`: this must start synchronously inside a `pagehide` handler, with a token the
 * caller already holds.
 */
export function postListens(token: string, events: ListenEvent[]): Promise<Response> {
  return fetch(`${BASE_URL}/activity/listens`, {
    method: "POST",
    keepalive: true,
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ events }),
  });
}

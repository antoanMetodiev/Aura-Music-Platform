import type { PlaylistSummary, Track } from "./catalog";

export interface UserSummary {
  id: string;
  username: string;
  displayName: string;
  avatarUrl?: string;
}

/** Ephemeral presence state delivered over Supabase Realtime (Project-Info.md §29). */
export type PresenceStatus = "online" | "listening" | "offline";

export interface FriendPresence {
  user: UserSummary;
  status: PresenceStatus;
  track?: Track;
  /** ISO timestamp of when the current track started. */
  startedAt?: string;
}

export type ActivityType =
  | "LISTENING"
  | "PLAYLIST_CREATED"
  | "TRACK_LIKED"
  | "FRIEND_ACCEPTED";

export interface ActivityItem {
  id: string;
  type: ActivityType;
  user: UserSummary;
  track?: Track;
  playlist?: PlaylistSummary;
  /** ISO timestamp. */
  occurredAt: string;
}

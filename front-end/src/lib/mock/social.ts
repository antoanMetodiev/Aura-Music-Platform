/**
 * TEMPORARY mock social data. Shapes match `@/types/social`.
 * Avatars are intentionally absent — `UserAvatar` renders initials on a
 * deterministic gradient so we don't depend on external avatar services.
 */
import type { ActivityItem, FriendPresence, UserSummary } from "@/types/social";
import { playlistById, trackById, tracks } from "./catalog";

export const currentUser: UserSummary = {
  id: "me",
  username: "antoan",
  displayName: "Antoan",
};

const user = (id: string, displayName: string): UserSummary => ({
  id,
  username: id,
  displayName,
});

export const friends = {
  ivan: user("ivan", "Ivan Petrov"),
  maria: user("maria", "Maria Georgieva"),
  george: user("george", "George Dimitrov"),
  elena: user("elena", "Elena Koleva"),
  nikola: user("nikola", "Nikola Stoyanov"),
  yana: user("yana", "Yana Ivanova"),
  petar: user("petar", "Petar Iliev"),
} satisfies Record<string, UserSummary>;

const minutesAgo = (m: number) => new Date(Date.now() - m * 60_000).toISOString();

export const friendPresence: FriendPresence[] = [
  { user: friends.ivan, status: "listening", track: trackById("nude"), startedAt: minutesAgo(1) },
  { user: friends.maria, status: "listening", track: trackById("the-less-i-know"), startedAt: minutesAgo(2) },
  { user: friends.george, status: "listening", track: trackById("blinding-lights"), startedAt: minutesAgo(0.5) },
  { user: friends.elena, status: "listening", track: trackById("kill-bill"), startedAt: minutesAgo(3) },
  { user: friends.nikola, status: "online" },
  { user: friends.yana, status: "offline" },
  { user: friends.petar, status: "offline" },
];

export const activityFeed: ActivityItem[] = [
  { id: "a1", type: "LISTENING", user: friends.ivan, track: trackById("nude"), occurredAt: minutesAgo(1) },
  { id: "a2", type: "LISTENING", user: friends.maria, track: trackById("the-less-i-know"), occurredAt: minutesAgo(2) },
  { id: "a3", type: "TRACK_LIKED", user: friends.george, track: trackById("blinding-lights"), occurredAt: minutesAgo(9) },
  { id: "a4", type: "PLAYLIST_CREATED", user: friends.elena, playlist: playlistById("night-rider"), occurredAt: minutesAgo(25) },
  { id: "a5", type: "LISTENING", user: friends.nikola, track: trackById("instant-crush"), occurredAt: minutesAgo(47) },
  { id: "a6", type: "TRACK_LIKED", user: friends.yana, track: trackById("pink-white"), occurredAt: minutesAgo(130) },
  { id: "a7", type: "FRIEND_ACCEPTED", user: friends.petar, occurredAt: minutesAgo(400) },
];

/** Tracks several friends played this week, with who played them. */
export const trendingAmongFriends: { track: (typeof tracks)[number]; listeners: UserSummary[] }[] = [
  { track: trackById("birds-of-a-feather")!, listeners: [friends.ivan, friends.maria, friends.elena, friends.yana] },
  { track: trackById("weird-fishes")!, listeners: [friends.ivan, friends.george] },
  { track: trackById("let-it-happen")!, listeners: [friends.maria, friends.nikola, friends.petar] },
  { track: trackById("humble")!, listeners: [friends.george, friends.elena] },
  { track: trackById("venice-bitch")!, listeners: [friends.yana] },
  { track: trackById("rosie")!, listeners: [friends.nikola, friends.ivan, friends.maria] },
];

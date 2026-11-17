import { apiFetch } from "@/lib/api/client";
import type { UserSummary } from "@/types/social";

/**
 * socials-svc (`/social/**`): friends, friend requests, follows, blocks and finding people.
 * Every call is user-scoped: client code passes `await getClientAccessToken()`, server components
 * `await getAccessToken()`.
 */

/** Where the viewer stands with another user. Someone who blocked the viewer is never returned at all. */
export type Relationship = "SELF" | "NONE" | "FRIENDS" | "REQUEST_SENT" | "REQUEST_RECEIVED" | "BLOCKED";

interface UserDto {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface Connection {
  user: UserSummary;
  /** ISO timestamp: friends since / asked at / blocked at. */
  since: string;
}

export interface PersonResult {
  user: UserSummary;
  relationship: Relationship;
}

export interface SocialProfile {
  user: UserSummary;
  relationship: Relationship;
  following: boolean;
  followsYou: boolean;
  friendCount: number;
  followerCount: number;
  followingCount: number;
}

export interface SocialSummary {
  friends: number;
  incomingRequests: number;
}

function toUser(dto: UserDto): UserSummary {
  return { id: dto.id, username: dto.username, displayName: dto.displayName, avatarUrl: dto.avatarUrl ?? undefined };
}

function toConnections(dtos: { user: UserDto; since: string }[]): Connection[] {
  return dtos.map((dto) => ({ user: toUser(dto.user), since: dto.since }));
}

// ── Reads ────────────────────────────────────────────────────────────────────────────────

export async function searchPeople(token: string | null, query: string, signal?: AbortSignal): Promise<PersonResult[]> {
  const dtos = await apiFetch<{ user: UserDto; relationship: Relationship }[]>("/social/users/search", {
    token,
    signal,
    searchParams: { q: query },
  });
  return dtos.map((dto) => ({ user: toUser(dto.user), relationship: dto.relationship }));
}

export async function getSocialProfile(token: string | null, username: string): Promise<SocialProfile> {
  const dto = await apiFetch<Omit<SocialProfile, "user"> & { user: UserDto }>(
    `/social/users/by-username/${encodeURIComponent(username)}`,
    { token },
  );
  return { ...dto, user: toUser(dto.user) };
}

export function getSocialSummary(token: string | null): Promise<SocialSummary> {
  return apiFetch<SocialSummary>("/social/me/summary", { token });
}

export async function getFriends(token: string | null): Promise<Connection[]> {
  return toConnections(await apiFetch("/social/friends", { token }));
}

export async function getIncomingRequests(token: string | null): Promise<Connection[]> {
  return toConnections(await apiFetch("/social/friend-requests/incoming", { token }));
}

export async function getOutgoingRequests(token: string | null): Promise<Connection[]> {
  return toConnections(await apiFetch("/social/friend-requests/outgoing", { token }));
}

export async function getBlockedUsers(token: string | null): Promise<Connection[]> {
  return toConnections(await apiFetch("/social/blocks", { token }));
}

// ── Writes (all idempotent on the server) ────────────────────────────────────────────────

/** `REQUEST_SENT`, or `FRIENDS` when they had already asked you. */
export async function sendFriendRequest(token: string | null, userId: string): Promise<Relationship> {
  const res = await apiFetch<{ relationship: Relationship }>("/social/friend-requests", {
    token,
    method: "POST",
    body: { userId },
  });
  return res.relationship;
}

export async function acceptFriendRequest(token: string | null, userId: string): Promise<Relationship> {
  const res = await apiFetch<{ relationship: Relationship }>(`/social/friend-requests/${userId}/accept`, {
    token,
    method: "POST",
  });
  return res.relationship;
}

/** Declines their request or cancels yours — whichever exists. */
export function removeFriendRequest(token: string | null, userId: string): Promise<void> {
  return apiFetch<void>(`/social/friend-requests/${userId}`, { token, method: "DELETE" });
}

export function unfriend(token: string | null, userId: string): Promise<void> {
  return apiFetch<void>(`/social/friends/${userId}`, { token, method: "DELETE" });
}

export function setFollowing(token: string | null, userId: string, follow: boolean): Promise<void> {
  return apiFetch<void>(`/social/follows/${userId}`, { token, method: follow ? "PUT" : "DELETE" });
}

export function setBlocked(token: string | null, userId: string, block: boolean): Promise<void> {
  return apiFetch<void>(`/social/blocks/${userId}`, { token, method: block ? "PUT" : "DELETE" });
}

"use client";

import { create } from "zustand";
import { getClientAccessToken } from "@/lib/api/token";
import {
  addTracksToPlaylist,
  createPlaylist,
  getMyPlaylists,
  likeTrack,
  likedContains,
  unlikeTrack,
  type LibraryPlaylist,
} from "../api/libraryApi";

/**
 * The signed-in user's library as the UI sees it: which tracks are liked (learned lazily, per id
 * a heart actually renders) and the playlist list for the sidebar and "Add to playlist" menus.
 * Writes are optimistic and roll back on failure.
 */
interface LibraryState {
  signedIn: boolean;
  /** trackId → liked. Absent = not asked yet. */
  liked: Record<string, boolean>;
  /** null until loaded (the sidebar falls back to what the server rendered). */
  playlists: LibraryPlaylist[] | null;
}

interface LibraryActions {
  init: (signedIn: boolean, playlists: LibraryPlaylist[] | null) => void;
  /** Queue ids whose liked state is unknown; one batched request per tick. */
  ensureLiked: (trackIds: string[]) => void;
  /** Returns the new state. Throws (after rolling back) when the server says no. */
  toggleLike: (trackId: string) => Promise<boolean>;
  setLiked: (trackId: string, liked: boolean) => void;
  refreshPlaylists: () => Promise<void>;
  createPlaylist: (title: string) => Promise<LibraryPlaylist>;
  addToPlaylist: (playlistId: string, trackIds: string[]) => Promise<void>;
  /** For pages that change a playlist themselves (rename, delete, remove a track). */
  replacePlaylists: (update: (current: LibraryPlaylist[]) => LibraryPlaylist[]) => void;
}

const CONTAINS_BATCH = 200;
let pending = new Set<string>();
let flushScheduled = false;

export const useLibraryStore = create<LibraryState & LibraryActions>((set, get) => ({
  signedIn: false,
  liked: {},
  playlists: null,

  init: (signedIn, playlists) => {
    if (!signedIn) {
      set({ signedIn: false, liked: {}, playlists: null });
      return;
    }
    set((s) => ({ signedIn: true, playlists: s.playlists ?? playlists }));
  },

  ensureLiked: (trackIds) => {
    const { signedIn, liked } = get();
    if (!signedIn) return;
    for (const id of trackIds) if (!(id in liked)) pending.add(id);
    if (pending.size === 0 || flushScheduled) return;
    flushScheduled = true;
    queueMicrotask(async () => {
      flushScheduled = false;
      const ids = [...pending];
      pending = new Set();
      const token = await getClientAccessToken();
      for (let i = 0; i < ids.length; i += CONTAINS_BATCH) {
        const chunk = ids.slice(i, i + CONTAINS_BATCH);
        try {
          const answers = await likedContains(token, chunk);
          set((s) => {
            const next = { ...s.liked };
            chunk.forEach((id, j) => {
              if (!(id in next)) next[id] = answers[j] ?? false;
            });
            return { liked: next };
          });
        } catch {
          // Library unreachable: hearts stay neutral; the next render asks again.
        }
      }
    });
  },

  toggleLike: async (trackId) => {
    const was = get().liked[trackId] ?? false;
    get().setLiked(trackId, !was);
    try {
      const token = await getClientAccessToken();
      await (was ? unlikeTrack(token, trackId) : likeTrack(token, trackId));
      return !was;
    } catch (error) {
      get().setLiked(trackId, was);
      throw error;
    }
  },

  setLiked: (trackId, liked) => set((s) => ({ liked: { ...s.liked, [trackId]: liked } })),

  refreshPlaylists: async () => {
    if (!get().signedIn) return;
    try {
      set({ playlists: await getMyPlaylists(await getClientAccessToken()) });
    } catch {
      // Keep whatever we had.
    }
  },

  createPlaylist: async (title) => {
    const created = await createPlaylist(await getClientAccessToken(), title);
    set((s) => ({ playlists: [created, ...(s.playlists ?? [])] }));
    return created;
  },

  addToPlaylist: async (playlistId, trackIds) => {
    await addTracksToPlaylist(await getClientAccessToken(), playlistId, trackIds);
    // Count and cover may have changed.
    void get().refreshPlaylists();
  },

  replacePlaylists: (update) => set((s) => ({ playlists: update(s.playlists ?? []) })),
}));

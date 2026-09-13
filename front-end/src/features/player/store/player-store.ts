import { create } from "zustand";
import type { Track } from "@/types/catalog";

export type RepeatMode = "off" | "all" | "one";

interface PlayerState {
  current: Track | null;
  queue: Track[];
  isPlaying: boolean;
  /** Playback position in ms. Updated by the playback adapter, never persisted per-second. */
  positionMs: number;
  volume: number; // 0..1
  muted: boolean;
  shuffle: boolean;
  repeat: RepeatMode;
}

interface PlayerActions {
  play: (track?: Track, queue?: Track[]) => void;
  pause: () => void;
  toggle: () => void;
  next: () => void;
  previous: () => void;
  seek: (positionMs: number) => void;
  setVolume: (volume: number) => void;
  toggleMute: () => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  /** Internal: called by the playback adapter's progress ticks. */
  _tick: (positionMs: number) => void;
}

export const usePlayerStore = create<PlayerState & PlayerActions>((set, get) => ({
  current: null,
  queue: [],
  isPlaying: false,
  positionMs: 0,
  volume: 0.8,
  muted: false,
  shuffle: false,
  repeat: "off",

  play: (track, queue) => {
    if (track) {
      set({ current: track, queue: queue ?? get().queue, positionMs: 0, isPlaying: true });
      return;
    }
    if (get().current) set({ isPlaying: true });
  },
  pause: () => set({ isPlaying: false }),
  toggle: () => (get().isPlaying ? get().pause() : get().play()),

  next: () => {
    const { current, queue } = get();
    if (!current) return;
    const index = queue.findIndex((t) => t.id === current.id);
    const nextTrack = queue[index + 1] ?? (get().repeat === "all" ? queue[0] : undefined);
    if (nextTrack) set({ current: nextTrack, positionMs: 0, isPlaying: true });
    else set({ isPlaying: false, positionMs: current.durationMs });
  },
  previous: () => {
    const { current, queue, positionMs } = get();
    if (!current) return;
    if (positionMs > 3000) return set({ positionMs: 0 });
    const index = queue.findIndex((t) => t.id === current.id);
    const prev = queue[index - 1];
    if (prev) set({ current: prev, positionMs: 0, isPlaying: true });
    else set({ positionMs: 0 });
  },

  seek: (positionMs) => set({ positionMs }),
  setVolume: (volume) => set({ volume: Math.min(1, Math.max(0, volume)), muted: false }),
  toggleMute: () => set((s) => ({ muted: !s.muted })),
  toggleShuffle: () => set((s) => ({ shuffle: !s.shuffle })),
  cycleRepeat: () =>
    set((s) => ({ repeat: s.repeat === "off" ? "all" : s.repeat === "all" ? "one" : "off" })),

  _tick: (positionMs) => {
    const { current, repeat } = get();
    if (!current) return;
    if (positionMs >= current.durationMs) {
      if (repeat === "one") return set({ positionMs: 0 });
      return get().next();
    }
    set({ positionMs });
  },
}));

/**
 * Derived helper. NOT a store selector: it returns a fresh array, which would
 * make `useStore(selector)` loop. Select `queue` + `current` and memoize instead.
 */
export const getUpNext = (queue: Track[], current: Track | null): Track[] => {
  if (!current) return queue;
  const index = queue.findIndex((t) => t.id === current.id);
  return index === -1 ? queue : queue.slice(index + 1);
};

import { create } from "zustand";

/**
 * Where the (single, app-wide) YouTube iframe should be shown. The Now Playing panel registers
 * its artwork box here while the user has chosen "video"; `PlaybackEngine` keeps the iframe
 * pinned over that box. Null = keep the iframe off-screen (audio only).
 */
interface VideoSurfaceState {
  slot: HTMLElement | null;
  setSlot: (slot: HTMLElement | null) => void;
}

export const useVideoSurfaceStore = create<VideoSurfaceState>((set) => ({
  slot: null,
  setSlot: (slot) => set({ slot }),
}));

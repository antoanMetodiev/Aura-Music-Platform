import { create } from "zustand";

/**
 * Where the (single, app-wide) YouTube iframe should be shown. Whoever renders a video box registers
 * it here while the user has chosen "video"; `PlaybackEngine` keeps the iframe pinned over the
 * winning box. Two layers can hold a box at once — the Now Playing panel and the full-screen
 * player — and the full-screen one wins while it's open, so closing it hands the video straight
 * back to the panel instead of parking it off-screen. No box at all = audio only.
 */
interface VideoSurfaceState {
  panelSlot: HTMLElement | null;
  fullscreenSlot: HTMLElement | null;
  setSlot: (slot: HTMLElement | null) => void;
  setFullscreenSlot: (slot: HTMLElement | null) => void;
}

export const useVideoSurfaceStore = create<VideoSurfaceState>((set) => ({
  panelSlot: null,
  fullscreenSlot: null,
  setSlot: (slot) => set({ panelSlot: slot }),
  setFullscreenSlot: (slot) => set({ fullscreenSlot: slot }),
}));

/** The box the iframe should currently sit over. */
export const selectActiveSlot = (s: VideoSurfaceState) => s.fullscreenSlot ?? s.panelSlot;

import { create } from "zustand";
import { persist } from "zustand/middleware";

export type RightPanelTab = "now-playing" | "queue" | "friends";

/** Resizable side panel bounds (px). Dragging below `collapseBelow` snaps the panel shut instead. */
export const SIDEBAR_SIZE = { min: 200, max: 440, default: 256, collapseBelow: 150 } as const;
export const RIGHT_PANEL_SIZE = { min: 280, max: 520, default: 320, collapseBelow: 200 } as const;

interface UiState {
  sidebarCollapsed: boolean;
  sidebarWidth: number;
  rightPanelOpen: boolean;
  rightPanelWidth: number;
  rightPanelTab: RightPanelTab;
  /** Now Playing panel shows the (monochrome) video instead of the artwork. */
  nowPlayingVideo: boolean;
  mobilePlayerExpanded: boolean;
  /** True while a panel edge is being dragged — panels drop their width transition so they track the pointer. */
  resizing: boolean;
}

interface UiActions {
  toggleSidebar: () => void;
  setSidebarWidth: (width: number) => void;
  toggleRightPanel: () => void;
  setRightPanelWidth: (width: number) => void;
  openRightPanel: (tab: RightPanelTab) => void;
  setRightPanelTab: (tab: RightPanelTab) => void;
  setNowPlayingVideo: (video: boolean) => void;
  setMobilePlayerExpanded: (open: boolean) => void;
  setResizing: (resizing: boolean) => void;
}

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, Math.round(value)));

export const useUiStore = create<UiState & UiActions>()(
  persist(
    (set, get) => ({
      sidebarCollapsed: false,
      sidebarWidth: SIDEBAR_SIZE.default,
      rightPanelOpen: true,
      rightPanelWidth: RIGHT_PANEL_SIZE.default,
      rightPanelTab: "friends",
      nowPlayingVideo: false,
      mobilePlayerExpanded: false,
      resizing: false,

      toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
      setSidebarWidth: (width) => {
        if (width < SIDEBAR_SIZE.collapseBelow) return set({ sidebarCollapsed: true });
        set({ sidebarCollapsed: false, sidebarWidth: clamp(width, SIDEBAR_SIZE.min, SIDEBAR_SIZE.max) });
      },
      toggleRightPanel: () => set((s) => ({ rightPanelOpen: !s.rightPanelOpen })),
      setRightPanelWidth: (width) => {
        if (width < RIGHT_PANEL_SIZE.collapseBelow) return set({ rightPanelOpen: false });
        set({ rightPanelOpen: true, rightPanelWidth: clamp(width, RIGHT_PANEL_SIZE.min, RIGHT_PANEL_SIZE.max) });
      },
      openRightPanel: (tab) => {
        // Clicking the already-active tab's button toggles the panel closed.
        if (get().rightPanelOpen && get().rightPanelTab === tab) return set({ rightPanelOpen: false });
        set({ rightPanelOpen: true, rightPanelTab: tab });
      },
      setRightPanelTab: (tab) => set({ rightPanelTab: tab }),
      setNowPlayingVideo: (video) => set({ nowPlayingVideo: video }),
      setMobilePlayerExpanded: (open) => set({ mobilePlayerExpanded: open }),
      setResizing: (resizing) => set({ resizing }),
    }),
    {
      name: "aura-ui",
      partialize: (s) => ({
        sidebarCollapsed: s.sidebarCollapsed,
        sidebarWidth: s.sidebarWidth,
        rightPanelOpen: s.rightPanelOpen,
        rightPanelWidth: s.rightPanelWidth,
        rightPanelTab: s.rightPanelTab,
        nowPlayingVideo: s.nowPlayingVideo,
      }),
      // Rehydrated after mount (see UiStateHydrator) so the server-rendered layout and the first
      // client render agree; the persisted sizes are applied a frame later.
      skipHydration: true,
    },
  ),
);

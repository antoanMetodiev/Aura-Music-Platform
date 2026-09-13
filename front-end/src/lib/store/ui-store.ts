import { create } from "zustand";

export type RightPanelTab = "now-playing" | "queue" | "friends";

interface UiState {
  sidebarCollapsed: boolean;
  rightPanelOpen: boolean;
  rightPanelTab: RightPanelTab;
  mobilePlayerExpanded: boolean;
}

interface UiActions {
  toggleSidebar: () => void;
  toggleRightPanel: () => void;
  openRightPanel: (tab: RightPanelTab) => void;
  setRightPanelTab: (tab: RightPanelTab) => void;
  setMobilePlayerExpanded: (open: boolean) => void;
}

export const useUiStore = create<UiState & UiActions>((set, get) => ({
  sidebarCollapsed: false,
  rightPanelOpen: true,
  rightPanelTab: "friends",
  mobilePlayerExpanded: false,

  toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
  toggleRightPanel: () => set((s) => ({ rightPanelOpen: !s.rightPanelOpen })),
  openRightPanel: (tab) => {
    // Clicking the already-active tab's button toggles the panel closed.
    if (get().rightPanelOpen && get().rightPanelTab === tab) return set({ rightPanelOpen: false });
    set({ rightPanelOpen: true, rightPanelTab: tab });
  },
  setRightPanelTab: (tab) => set({ rightPanelTab: tab }),
  setMobilePlayerExpanded: (open) => set({ mobilePlayerExpanded: open }),
}));

"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { RIGHT_PANEL_SIZE, SIDEBAR_SIZE, useUiStore } from "@/lib/store/ui-store";

/** Icon-rail width the sidebar collapses to — the drag starts from here when it is collapsed. */
const SIDEBAR_RAIL_WIDTH = 68;

interface ResizeHandleProps {
  /** Which panel this edge belongs to. */
  panel: "sidebar" | "right-panel";
  className?: string;
}

/**
 * The 8px gutter between a side panel and the main area, draggable like Spotify's: a thin line
 * appears on hover, dragging resizes the panel, dragging past its minimum snaps it shut (the
 * sidebar to its icon rail, the right panel closed), and a double-click restores the default width.
 */
export function ResizeHandle({ panel, className }: ResizeHandleProps) {
  const t = useTranslations(panel === "sidebar" ? "nav" : "panel");
  const resizing = useUiStore((s) => s.resizing);
  const dragRef = useRef<{ pointerId: number; startX: number; startWidth: number } | null>(null);

  const onPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    const state = useUiStore.getState();
    const startWidth =
      panel === "sidebar"
        ? state.sidebarCollapsed
          ? SIDEBAR_RAIL_WIDTH
          : state.sidebarWidth
        : state.rightPanelWidth;
    dragRef.current = { pointerId: event.pointerId, startX: event.clientX, startWidth };
    event.currentTarget.setPointerCapture(event.pointerId);
    state.setResizing(true);
  };

  const onPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const dx = event.clientX - drag.startX;
    const state = useUiStore.getState();
    if (panel === "sidebar") {
      state.setSidebarWidth(drag.startWidth + dx);
    } else {
      state.setRightPanelWidth(drag.startWidth - dx);
    }
  };

  const endDrag = (event: React.PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    dragRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    useUiStore.getState().setResizing(false);
  };

  const reset = () => {
    const state = useUiStore.getState();
    if (panel === "sidebar") state.setSidebarWidth(SIDEBAR_SIZE.default);
    else state.setRightPanelWidth(RIGHT_PANEL_SIZE.default);
  };

  // Dragging the right panel shut unmounts this handle before pointerup arrives — end the drag here
  // so `resizing` (and the body cursor) don't stay stuck.
  useEffect(
    () => () => {
      if (dragRef.current) {
        dragRef.current = null;
        useUiStore.getState().setResizing(false);
      }
    },
    [],
  );

  // Body-wide cursor + no text selection while dragging, even when the pointer leaves the gutter.
  useEffect(() => {
    if (!resizing) return;
    const { cursor, userSelect } = document.body.style;
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    return () => {
      document.body.style.cursor = cursor;
      document.body.style.userSelect = userSelect;
    };
  }, [resizing]);

  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label={t("resize")}
      title={t("resizeHint")}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
      onDoubleClick={reset}
      className={cn("group/resize relative w-2 shrink-0 cursor-col-resize touch-none select-none", className)}
    >
      <div
        className={cn(
          "absolute inset-y-3 left-1/2 w-px -translate-x-1/2 rounded-full bg-border-strong opacity-0 transition-opacity",
          "group-hover/resize:opacity-100",
          resizing && "bg-foreground/70 opacity-100",
        )}
      />
    </div>
  );
}

/** Applies the persisted panel layout after mount, so SSR and the first client render match. */
export function UiStateHydrator() {
  useEffect(() => {
    void useUiStore.persist.rehydrate();
  }, []);
  return null;
}

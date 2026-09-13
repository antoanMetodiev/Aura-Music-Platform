"use client";

import { useEffect } from "react";
import { usePlayerStore } from "../store/player-store";

/**
 * Mounted once in the app shell. Drives `positionMs` while playing.
 *
 * TEMPORARY: a plain interval stands in for the real playback adapter. When the
 * YouTube IFrame adapter lands it replaces this component and feeds the same
 * `_tick` / `next` actions from real player events — nothing else changes.
 */
export function PlaybackEngine() {
  const isPlaying = usePlayerStore((s) => s.isPlaying);

  useEffect(() => {
    if (!isPlaying) return;
    const step = 250;
    const id = window.setInterval(() => {
      const { positionMs, _tick } = usePlayerStore.getState();
      _tick(positionMs + step);
    }, step);
    return () => window.clearInterval(id);
  }, [isPlaying]);

  // Space toggles playback when focus isn't in a text field.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.code !== "Space") return;
      const target = event.target as HTMLElement | null;
      const typing =
        target?.tagName === "INPUT" ||
        target?.tagName === "TEXTAREA" ||
        target?.tagName === "BUTTON" ||
        target?.isContentEditable;
      if (typing) return;
      event.preventDefault();
      usePlayerStore.getState().toggle();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return null;
}

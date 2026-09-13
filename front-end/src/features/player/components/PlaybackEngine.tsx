"use client";

import { useEffect, useRef, useState } from "react";
import { resolvePlaybackSource } from "../api/playbackApi";
import { createYouTubePlayer, loadYouTubeIframeApi, YouTubePlayerState, type YouTubePlayer } from "../lib/youtubeIframeApi";
import { usePlayerStore } from "../store/player-store";

const HOST_ELEMENT_ID = "aura-youtube-player-host";

/**
 * Mounted once in the app shell. Drives real playback through YouTube's official IFrame Player API
 * (Project-Info.md §38 — the backend only ever hands us a video id, never a direct audio URL).
 *
 * The player itself stays completely invisible: native controls are disabled via `playerVars` and
 * the host element is sized to 1x1 and positioned off-screen, so only our own Spotify-style
 * transport UI (`PlaybackControls`, `ProgressBar`, `VolumeControl`) is ever seen or used.
 *
 * Every video load starts muted, then unmutes the instant real playback begins. Chrome (and most
 * browsers) always allow autoplay when muted, but block autoplay-with-sound unless it happens
 * synchronously within a user gesture — and ours never does, since a network round trip to resolve
 * the track sits between the click and `loadVideoById`. Unmuting an already-playing video doesn't
 * need a fresh gesture, so this sidesteps the restriction without ever being audible-then-cut.
 */
export function PlaybackEngine() {
  const [isReady, setIsReady] = useState(false);
  const playerRef = useRef<YouTubePlayer | null>(null);
  const loadedTrackIdRef = useRef<string | null>(null);
  const seenSeekVersionRef = useRef<number | null>(null);
  const pendingUnmuteRef = useRef(false);

  const current = usePlayerStore((s) => s.current);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const volume = usePlayerStore((s) => s.volume);
  const muted = usePlayerStore((s) => s.muted);
  const seekVersion = usePlayerStore((s) => s.seekVersion);

  // Create the (invisible) player once.
  useEffect(() => {
    let cancelled = false;
    loadYouTubeIframeApi().then(() => {
      if (cancelled) return;
      playerRef.current = createYouTubePlayer(HOST_ELEMENT_ID, {
        height: "1",
        width: "1",
        playerVars: {
          controls: 0,
          disablekb: 1,
          fs: 0,
          iv_load_policy: 3,
          modestbranding: 1,
          rel: 0,
          playsinline: 1,
        },
        events: {
          onReady: () => setIsReady(true),
          onStateChange: (event) => {
            const unmuteableStates: number[] = [YouTubePlayerState.PLAYING, YouTubePlayerState.PAUSED, YouTubePlayerState.BUFFERING];
            if (pendingUnmuteRef.current && unmuteableStates.includes(event.data)) {
              pendingUnmuteRef.current = false;
              const player = playerRef.current;
              const { muted: shouldStayMuted, volume: currentVolume } = usePlayerStore.getState();
              if (player && !shouldStayMuted) {
                player.unMute();
                player.setVolume(Math.round(currentVolume * 100));
              }
            }
            if (event.data === YouTubePlayerState.ENDED) usePlayerStore.getState().next();
          },
        },
      });
    });
    return () => {
      cancelled = true;
      playerRef.current?.destroy();
      playerRef.current = null;
    };
  }, []);

  // Load a new video whenever the current track changes (and once the player becomes ready).
  useEffect(() => {
    if (!isReady || !playerRef.current) return;
    if (!current) {
      playerRef.current.pauseVideo();
      loadedTrackIdRef.current = null;
      return;
    }
    if (loadedTrackIdRef.current === current.id) return;
    loadedTrackIdRef.current = current.id;

    resolvePlaybackSource(current.id)
      .then((source) => {
        // The user may have already skipped again while this was in flight.
        if (loadedTrackIdRef.current !== current.id) return;
        const player = playerRef.current;
        if (!source || !player) {
          usePlayerStore.getState().pause();
          return;
        }
        pendingUnmuteRef.current = true;
        player.mute();
        player.loadVideoById(source.providerResourceId, 0);
        player.playVideo();
      })
      .catch(() => {
        if (loadedTrackIdRef.current === current.id) usePlayerStore.getState().pause();
      });
  }, [current, isReady]);

  // Play / pause.
  useEffect(() => {
    if (!isReady || !playerRef.current) return;
    if (isPlaying) playerRef.current.playVideo();
    else playerRef.current.pauseVideo();
  }, [isPlaying, isReady]);

  // Volume / mute — skipped while a fresh load is still waiting to unmute itself (see above).
  useEffect(() => {
    if (!isReady || !playerRef.current || pendingUnmuteRef.current) return;
    playerRef.current.setVolume(Math.round(volume * 100));
  }, [volume, isReady]);

  useEffect(() => {
    if (!isReady || !playerRef.current || pendingUnmuteRef.current) return;
    if (muted) playerRef.current.mute();
    else playerRef.current.unMute();
  }, [muted, isReady]);

  // User-initiated seeks (ProgressBar) — `seekVersion` only changes from `seek()`, never from our
  // own `_tick()` writes below, so this never fights the position it's currently reporting.
  useEffect(() => {
    if (seenSeekVersionRef.current === null) {
      seenSeekVersionRef.current = seekVersion; // skip the initial mount value
      return;
    }
    if (seenSeekVersionRef.current === seekVersion) return;
    seenSeekVersionRef.current = seekVersion;
    if (!isReady || !playerRef.current) return;
    playerRef.current.seekTo(usePlayerStore.getState().positionMs / 1000, true);
  }, [seekVersion, isReady]);

  // Poll real playback position while playing — YouTube's IFrame API has no progress event.
  useEffect(() => {
    if (!isPlaying || !isReady) return;
    const id = window.setInterval(() => {
      const player = playerRef.current;
      if (!player) return;
      usePlayerStore.getState()._tick(Math.round(player.getCurrentTime() * 1000));
    }, 250);
    return () => window.clearInterval(id);
  }, [isPlaying, isReady]);

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

  return (
    <div
      id={HOST_ELEMENT_ID}
      aria-hidden
      className="pointer-events-none fixed bottom-0 right-0 size-px overflow-hidden opacity-0"
    />
  );
}

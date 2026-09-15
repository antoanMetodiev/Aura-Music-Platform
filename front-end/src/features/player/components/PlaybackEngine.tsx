"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/client";
import { useUiStore } from "@/lib/store/ui-store";
import { resolvePlaybackSource } from "../api/playbackApi";
import { selectActiveSlot, useVideoSurfaceStore } from "../store/video-surface-store";
import { VideoSurfaceOverlay } from "./VideoSurfaceOverlay";
import { createYouTubePlayer, loadYouTubeIframeApi, YouTubePlayerState, type YouTubePlayer } from "../lib/youtubeIframeApi";
import { usePlayerStore } from "../store/player-store";

const HOST_ELEMENT_ID = "aura-youtube-player-host";
/**
 * How much larger than its box the iframe is drawn when shown as video. The overflow is clipped,
 * which crops away YouTube's own top title bar and bottom control/suggestion strips — only the
 * picture itself remains.
 */
const VIDEO_CROP_ZOOM = 1.4;

function nearestScrollContainer(el: HTMLElement): HTMLElement | null {
  for (let node = el.parentElement; node; node = node.parentElement) {
    const { overflowY } = getComputedStyle(node);
    if (overflowY === "auto" || overflowY === "scroll") return node;
  }
  return null;
}

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
  const t = useTranslations("player");
  const [isReady, setIsReady] = useState(false);
  const playerRef = useRef<YouTubePlayer | null>(null);
  /** Track whose source we last asked for — guards against a stale resolve landing after another skip. */
  const loadedTrackIdRef = useRef<string | null>(null);
  /**
   * Track whose video is actually in the iframe right now. Differs from `loadedTrackIdRef` for the
   * whole resolve round trip; while they differ the play/pause and progress effects must leave the
   * player alone, or the *previous* song resumes for a second before the new one loads.
   */
  const playerTrackIdRef = useRef<string | null>(null);
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
            usePlayerStore.getState()._setEngineState(
              event.data === YouTubePlayerState.PLAYING ? "playing"
                : event.data === YouTubePlayerState.BUFFERING ? "buffering"
                : event.data === YouTubePlayerState.PAUSED ? "paused"
                : "idle",
            );
            if (event.data === YouTubePlayerState.ENDED) usePlayerStore.getState().next();
          },
          // 100 = removed/private, 101/150 = embedding disabled by the owner. Nothing we can do for
          // this track — move on, like a skipped unavailable song.
          onError: (event) => {
            console.warn("YouTube player error", event.data);
            usePlayerStore.getState()._setEngineState("idle");
            usePlayerStore.getState().next();
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
      playerTrackIdRef.current = null;
      return;
    }
    if (loadedTrackIdRef.current === current.id) return;
    loadedTrackIdRef.current = current.id;

    // Silence whatever is in the iframe *now*, before the network round trip — nothing of the
    // previous track may be heard while the next one resolves. (pauseVideo, not stopVideo: the
    // latter can report ENDED and would advance the queue.)
    playerTrackIdRef.current = null;
    playerRef.current.pauseVideo();
    playerRef.current.mute();

    resolvePlaybackSource(current.id)
      .then((source) => {
        // The user may have already skipped again while this was in flight.
        if (loadedTrackIdRef.current !== current.id) return;
        const player = playerRef.current;
        if (!player) return;
        if (!source) {
          // No confident source for this track (Project-Info.md §18: better silence than the wrong
          // song). Like Spotify, skip to the next track in the queue; stop if there is none.
          toast(t("unavailableSkipped", { title: current.title }));
          const store = usePlayerStore.getState();
          const queue = store.queue;
          const index = queue.findIndex((t) => t.id === current.id);
          if (index !== -1 && index < queue.length - 1) store.next();
          else store.pause();
          return;
        }
        pendingUnmuteRef.current = true;
        player.mute();
        player.loadVideoById(source.providerResourceId, 0);
        playerTrackIdRef.current = current.id;
        // Honour a pause pressed during the resolve instead of blindly starting.
        if (usePlayerStore.getState().isPlaying) player.playVideo();
        else player.pauseVideo();
      })
      .catch((error: unknown) => {
        if (loadedTrackIdRef.current !== current.id) return;
        usePlayerStore.getState().pause();
        const code = error instanceof ApiError ? error.code : "UNKNOWN_ERROR";
        toast.error(code === "PLAYBACK_QUOTA_EXHAUSTED" ? t("quotaExhausted") : t("playbackFailed"));
      });
  }, [current, isReady, t]);

  // Play / pause — only once the iframe holds the current track; during a resolve the load above
  // decides what to do when the video lands.
  useEffect(() => {
    if (!isReady || !playerRef.current) return;
    if (playerTrackIdRef.current !== current?.id) return;
    if (isPlaying) playerRef.current.playVideo();
    else playerRef.current.pauseVideo();
  }, [isPlaying, isReady, current]);

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
    if (playerTrackIdRef.current !== usePlayerStore.getState().current?.id) return;
    playerRef.current.seekTo(usePlayerStore.getState().positionMs / 1000, true);
  }, [seekVersion, isReady]);

  // Poll real playback position while playing — YouTube's IFrame API has no progress event.
  useEffect(() => {
    if (!isPlaying || !isReady) return;
    const id = window.setInterval(() => {
      const player = playerRef.current;
      if (!player) return;
      // While the next track resolves the iframe still holds the previous video — its time is not ours.
      if (playerTrackIdRef.current !== usePlayerStore.getState().current?.id) return;
      // Undefined/NaN while a freshly loaded video has no media time yet — skip the tick rather than
      // push NaN into the store (and from there into the seek slider).
      const seconds = player.getCurrentTime();
      if (!Number.isFinite(seconds)) return;
      usePlayerStore.getState()._tick(Math.round(seconds * 1000));
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

  // Video surface: while the Now Playing panel (or the full-screen player) asks for video, keep the
  // iframe pinned over the box it registered (measured every frame — panels resize, the page scrolls). Otherwise park it
  // off-screen at 1x1. The iframe is never moved in the DOM (that would reload it and cut the
  // audio); only the wrapper's position changes.
  const wrapperRef = useRef<HTMLDivElement>(null);
  const slot = useVideoSurfaceStore(selectActiveSlot);
  const videoWanted = useUiStore((s) => s.nowPlayingVideo);
  const showVideo = videoWanted && !!slot && !!current;

  useEffect(() => {
    const wrapper = wrapperRef.current;
    if (!wrapper) return;
    const iframe = document.getElementById(HOST_ELEMENT_ID);
    if (!showVideo || !slot) {
      Object.assign(wrapper.style, { left: "auto", right: "0px", top: "auto", bottom: "0px", width: "1px", height: "1px", opacity: "0", clipPath: "none" });
      if (iframe) Object.assign(iframe.style, { position: "absolute", width: "1px", height: "1px", left: "0px", top: "0px" });
      return;
    }
    // The panel scrolls; a fixed wrapper doesn't. Clip it to the scroll container's visible box so
    // the video slides under the panel header instead of floating over it.
    const scroller = nearestScrollContainer(slot);
    let frame = 0;
    const place = () => {
      const r = slot.getBoundingClientRect();
      const v = scroller ? scroller.getBoundingClientRect() : r;
      const clip = {
        top: Math.max(0, v.top - r.top),
        right: Math.max(0, r.right - v.right),
        bottom: Math.max(0, r.bottom - v.bottom),
        left: Math.max(0, v.left - r.left),
      };
      const hidden = clip.top >= r.height || clip.bottom >= r.height || clip.left >= r.width || clip.right >= r.width;
      Object.assign(wrapper.style, {
        left: `${r.left}px`, top: `${r.top}px`, right: "auto", bottom: "auto",
        width: `${r.width}px`, height: `${r.height}px`,
        opacity: hidden ? "0" : "1",
        clipPath: `inset(${clip.top}px ${clip.right}px ${clip.bottom}px ${clip.left}px round 0.5rem)`,
      });
      const target = document.getElementById(HOST_ELEMENT_ID);
      if (target) {
        const h = r.height * VIDEO_CROP_ZOOM;
        const w = Math.max(h * (16 / 9), r.width * VIDEO_CROP_ZOOM);
        Object.assign(target.style, { position: "absolute", width: `${w}px`, height: `${h}px`, left: `${(r.width - w) / 2}px`, top: `${(r.height - h) / 2}px` });
      }
      frame = requestAnimationFrame(place);
    };
    place();
    return () => cancelAnimationFrame(frame);
  }, [showVideo, slot]);

  return (
    <div
      ref={wrapperRef}
      aria-hidden
      className="pointer-events-none fixed bottom-0 right-0 z-40 size-px overflow-hidden rounded-lg bg-black opacity-0 [filter:grayscale(1)_contrast(1.08)]"
    >
      {/* Replaced by the YouTube iframe (same id) once the API is ready; sized/cropped from the effect above. */}
      <div id={HOST_ELEMENT_ID} className="absolute" />
      {showVideo && <VideoSurfaceOverlay />}
    </div>
  );
}

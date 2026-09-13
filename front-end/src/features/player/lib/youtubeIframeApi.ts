/**
 * Loads the YouTube IFrame Player API script exactly once and resolves when `window.YT.Player` is
 * ready to construct (Project-Info.md §38: playback goes through YouTube's official player, never
 * a direct audio URL). Safe to call from multiple components — later calls reuse the same promise.
 */

// Minimal surface of the global `YT` namespace the IFrame API script attaches — not the full
// type-fest, just what this feature actually calls.
export interface YouTubePlayer {
  playVideo(): void;
  pauseVideo(): void;
  loadVideoById(videoId: string, startSeconds?: number): void;
  seekTo(seconds: number, allowSeekAhead: boolean): void;
  setVolume(volume: number): void;
  mute(): void;
  unMute(): void;
  getCurrentTime(): number;
  getPlayerState(): number;
  destroy(): void;
}

export const YouTubePlayerState = {
  ENDED: 0,
  PLAYING: 1,
  PAUSED: 2,
  BUFFERING: 3,
  CUED: 5,
} as const;

interface YouTubePlayerOptions {
  height: string;
  width: string;
  playerVars: Record<string, number | string>;
  events: {
    onReady?: () => void;
    onStateChange?: (event: { data: number }) => void;
    onError?: (event: { data: number }) => void;
  };
}

declare global {
  interface Window {
    YT?: {
      Player: new (elementId: string, options: YouTubePlayerOptions) => YouTubePlayer;
    };
    onYouTubeIframeAPIReady?: () => void;
  }
}

let apiReadyPromise: Promise<void> | null = null;

export function loadYouTubeIframeApi(): Promise<void> {
  if (typeof window === "undefined") return Promise.reject(new Error("YouTube IFrame API requires a browser"));
  if (window.YT?.Player) return Promise.resolve();
  if (apiReadyPromise) return apiReadyPromise;

  apiReadyPromise = new Promise((resolve) => {
    const previousCallback = window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady = () => {
      previousCallback?.();
      resolve();
    };

    if (!document.querySelector('script[src="https://www.youtube.com/iframe_api"]')) {
      const script = document.createElement("script");
      script.src = "https://www.youtube.com/iframe_api";
      script.async = true;
      document.head.appendChild(script);
    }
  });

  return apiReadyPromise;
}

export function createYouTubePlayer(elementId: string, options: YouTubePlayerOptions): YouTubePlayer {
  if (!window.YT?.Player) throw new Error("YouTube IFrame API not loaded yet");
  return new window.YT.Player(elementId, options);
}

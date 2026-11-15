"use client";

import { useEffect } from "react";
import { getClientAccessToken } from "@/lib/api/token";
import { usePlayerStore } from "@/features/player/store/player-store";
import { postListens, type ListenEvent } from "../api/activityApi";

/**
 * Turns the player into durable listening history (Project-Info.md §32): one event per play, sent
 * when the play ends — on track change, on a repeat-one restart, or when the tab goes away — never
 * per-second player state. Time is counted only while the engine actually plays, so pauses and
 * buffering don't inflate it. Plays not yet sent wait in localStorage and go with the next flush.
 * Guests have no history: nothing is tracked for them.
 */

const QUEUE_KEY = "aura.pending-listens";
const MAX_QUEUED = 200;
/** Shorter than this isn't a play at all (a mis-click, an instant skip). */
const MIN_REPORTED_MS = 1000;
/** Heard at least this share of the track = completed. */
const COMPLETED_SHARE = 0.9;
const BATCH = 50;

interface Play {
  trackId: string;
  durationMs: number;
  startedAt: number;
  heardMs: number;
  /** Wall-clock start of the current stretch of real playback, or null while not playing. */
  playingSince: number | null;
  lastPositionMs: number;
}

const sessionId = typeof crypto !== "undefined" ? crypto.randomUUID() : "00000000-0000-4000-8000-000000000000";
let play: Play | null = null;
let token: string | null = null;
let flushTimer: ReturnType<typeof setTimeout> | null = null;

function readQueue(): ListenEvent[] {
  try {
    return JSON.parse(localStorage.getItem(QUEUE_KEY) ?? "[]") as ListenEvent[];
  } catch {
    return [];
  }
}

function writeQueue(events: ListenEvent[]) {
  try {
    localStorage.setItem(QUEUE_KEY, JSON.stringify(events.slice(-MAX_QUEUED)));
  } catch {
    // Private mode / storage full: the play is lost, playback is unaffected.
  }
}

function heard(p: Play, now: number) {
  return p.heardMs + (p.playingSince === null ? 0 : now - p.playingSince);
}

/** Ends the current play and queues it if it counts. */
function finish(now: number) {
  if (!play) return;
  const ms = Math.round(heard(play, now));
  if (ms >= MIN_REPORTED_MS) {
    writeQueue([
      ...readQueue(),
      {
        eventId: crypto.randomUUID(),
        eventVersion: 1,
        trackId: play.trackId,
        sessionId,
        startedAt: new Date(play.startedAt).toISOString(),
        endedAt: new Date(now).toISOString(),
        durationPlayedMs: ms,
        completed: ms >= play.durationMs * COMPLETED_SHARE,
        source: "web",
      },
    ]);
    scheduleFlush();
  }
  play = null;
}

function scheduleFlush() {
  if (flushTimer) clearTimeout(flushTimer);
  flushTimer = setTimeout(() => void flush(), 2000);
}

/** Sends what's queued. Kept on failure — the next play (or the next visit) tries again. */
async function flush(useCachedToken = false) {
  const queued = readQueue();
  if (queued.length === 0) return;
  const t = useCachedToken ? token : (token = await getClientAccessToken());
  if (!t) return;
  const batch = queued.slice(0, BATCH);
  try {
    const response = await postListens(t, batch);
    // 4xx other than auth means the batch itself is bad — drop it rather than retry forever.
    if (response.ok || (response.status >= 400 && response.status < 500 && response.status !== 401)) {
      const sent = new Set(batch.map((e) => e.eventId));
      writeQueue(readQueue().filter((e) => !sent.has(e.eventId)));
      if (queued.length > BATCH) scheduleFlush();
    }
  } catch {
    // Offline or backend asleep — try later.
  }
}

function onPlayerChange() {
  const { current, engineState, positionMs } = usePlayerStore.getState();
  const now = Date.now();

  // Repeat-one: same track, position jumped from the end back to the start = a new play.
  const restarted = play && current?.id === play.trackId && play.lastPositionMs > play.durationMs * COMPLETED_SHARE && positionMs < 5000;
  if (play && (current?.id !== play.trackId || restarted)) finish(now);

  if (current && !play) {
    play = { trackId: current.id, durationMs: current.durationMs, startedAt: now, heardMs: 0, playingSince: null, lastPositionMs: 0 };
  }
  if (!play) return;

  const playing = engineState === "playing";
  if (playing && play.playingSince === null) play.playingSince = now;
  if (!playing && play.playingSince !== null) {
    play.heardMs += now - play.playingSince;
    play.playingSince = null;
  }
  play.lastPositionMs = positionMs;
}

export function ListenTracker({ signedIn }: { signedIn: boolean }) {
  useEffect(() => {
    if (!signedIn) return;
    void getClientAccessToken().then((t) => (token = t));
    void flush(); // anything left from an earlier visit

    onPlayerChange();
    const unsubscribe = usePlayerStore.subscribe(onPlayerChange);
    const onPageHide = () => {
      finish(Date.now());
      void flush(true);
    };
    window.addEventListener("pagehide", onPageHide);
    return () => {
      unsubscribe();
      window.removeEventListener("pagehide", onPageHide);
    };
  }, [signedIn]);

  return null;
}

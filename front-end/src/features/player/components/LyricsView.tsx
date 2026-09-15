"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import type { LyricsDto } from "@/types/api";
import { usePlayerStore } from "../store/player-store";

/** Lead the highlight by this much — a line lighting up a hair early reads as "in time", late reads as lag. */
const LEAD_MS = 150;
/** After the user scrolls the lyrics by hand, leave them alone for this long before following the song again. */
const USER_SCROLL_HOLD_MS = 4000;
/** Where in the viewport the active line is kept (fraction of the container height from the top). */
const ACTIVE_LINE_ANCHOR = 0.4;

interface LyricsViewProps {
  lyrics: LyricsDto;
  className?: string;
}

/**
 * The text column of the full-screen player (todo.md §3.2). Synced lyrics follow `positionMs`: the
 * current line is large and bright, the ones before it dimmed, the ones after grey; the container
 * auto-scrolls so the current line sits ~40% down, and clicking any line seeks to it. Plain-only
 * lyrics render as paragraphs with no highlight.
 */
export function LyricsView({ lyrics, className }: LyricsViewProps) {
  if (lyrics.synced && lyrics.synced.length > 0) {
    return <SyncedLyrics lines={lyrics.synced} className={className} />;
  }
  return (
    <div className={cn("scrollbar-thin overflow-y-auto", className)}>
      <div className="whitespace-pre-line text-xl leading-relaxed font-medium text-foreground/85 sm:text-2xl">{lyrics.plain}</div>
    </div>
  );
}

function SyncedLyrics({ lines, className }: { lines: { timeMs: number; text: string }[]; className?: string }) {
  const t = useTranslations("player");
  const positionMs = usePlayerStore((s) => s.positionMs);
  const seek = usePlayerStore((s) => s.seek);

  // Last line whose time has passed; -1 before the first one.
  const activeIndex = findActiveIndex(lines, positionMs + LEAD_MS);

  const containerRef = useRef<HTMLOListElement>(null);
  const userScrolledAtRef = useRef(0);

  // Only *manual* scrolling should pause the follow — programmatic smooth scrolling fires `scroll`
  // events too, so listen to the gestures instead of the result.
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const mark = () => {
      userScrolledAtRef.current = Date.now();
    };
    el.addEventListener("wheel", mark, { passive: true });
    el.addEventListener("touchmove", mark, { passive: true });
    return () => {
      el.removeEventListener("wheel", mark);
      el.removeEventListener("touchmove", mark);
    };
  }, []);

  useEffect(() => {
    const el = containerRef.current;
    if (!el || activeIndex < 0) return;
    if (Date.now() - userScrolledAtRef.current < USER_SCROLL_HOLD_MS) return;
    const line = el.children[activeIndex] as HTMLElement | undefined;
    if (!line) return;
    const target = line.offsetTop - el.clientHeight * ACTIVE_LINE_ANCHOR + line.offsetHeight / 2;
    el.scrollTo({ top: Math.max(0, target), behavior: "smooth" });
  }, [activeIndex]);

  return (
    <ol
      ref={containerRef}
      aria-label={t("lyrics")}
      className={cn(
        "scrollbar-none relative overflow-y-auto scroll-smooth py-[30vh]",
        "[mask-image:linear-gradient(to_bottom,transparent,black_12%,black_88%,transparent)]",
        className,
      )}
    >
      {lines.map((line, i) => {
        const state = i === activeIndex ? "active" : i < activeIndex ? "past" : "next";
        const pause = line.text.length === 0 || line.text === "♪";
        return (
          <li key={`${line.timeMs}-${i}`} aria-current={state === "active" ? "true" : undefined}>
            <button
              type="button"
              onClick={() => seek(line.timeMs)}
              className={cn(
                "block w-full origin-left cursor-pointer py-2 text-left text-2xl font-bold tracking-tight transition-[color,transform,opacity] duration-300 ease-out sm:text-3xl lg:text-4xl",
                "hover:text-foreground focus-visible:text-foreground focus-visible:outline-none",
                state === "active" && "scale-100 text-foreground",
                state === "past" && "scale-[0.97] text-foreground/35",
                state === "next" && "scale-[0.97] text-foreground/55",
                pause && "py-4",
              )}
            >
              {pause ? <PauseDots active={state === "active"} /> : line.text}
            </button>
          </li>
        );
      })}
    </ol>
  );
}

/** Three dots standing in for an instrumental gap; they pulse while it's the current "line". */
function PauseDots({ active }: { active: boolean }) {
  return (
    <span aria-hidden className="inline-flex gap-1.5">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className={cn("size-2 rounded-full bg-current", active && "animate-pulse")}
          style={active ? { animationDelay: `${i * 200}ms` } : undefined}
        />
      ))}
    </span>
  );
}

/** Index of the last line with `timeMs <= position` (binary search — lines are sorted by time). */
function findActiveIndex(lines: { timeMs: number }[], position: number): number {
  let lo = 0;
  let hi = lines.length - 1;
  let result = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (lines[mid].timeMs <= position) {
      result = mid;
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }
  return result;
}

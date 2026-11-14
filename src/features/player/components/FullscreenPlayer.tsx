"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronDown, Clapperboard, ImageIcon, MicVocal } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";
import { useUiStore } from "@/lib/store/ui-store";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import type { LyricsDto } from "@/types/api";
import { getLyrics } from "../api/lyricsApi";
import { usePlayerStore } from "../store/player-store";
import { useVideoSurfaceStore } from "../store/video-surface-store";
import { LyricsView } from "./LyricsView";
import { PlaybackControls } from "./PlaybackControls";
import { ProgressBar } from "./ProgressBar";

/** What the last finished lookup produced, tagged with its track so "loading" is simply "not this track yet". */
type LyricsResult = { trackId: string } & ({ status: "ready"; lyrics: LyricsDto | null } | { status: "error" });

/**
 * Full-screen "Now playing" (todo.md §3.2): artwork or video on the left, lyrics that follow the
 * song on the right, transport at the bottom. Opened from the player bar, closed with Esc or the
 * chevron. Sits under the pinned YouTube frame (z-40) so the video surface works here too, and
 * above everything else in the shell.
 */
export function FullscreenPlayer() {
  const t = useTranslations("player");
  const panel = useTranslations("panel");
  const open = useUiStore((s) => s.fullscreenPlayerOpen);
  const setOpen = useUiStore((s) => s.setFullscreenPlayerOpen);
  const video = useUiStore((s) => s.nowPlayingVideo);
  const setVideo = useUiStore((s) => s.setNowPlayingVideo);
  const current = usePlayerStore((s) => s.current);
  const setSlot = useVideoSurfaceStore((s) => s.setFullscreenSlot);

  const close = useCallback(() => setOpen(false), [setOpen]);

  // Nothing to show without a track — also covers the song ending with an empty queue while open.
  useEffect(() => {
    if (open && !current) close();
  }, [open, current, close]);

  // Esc closes; focus moves into the dialog on open and back to the opener on close.
  const dialogRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (!open) return;
    openerRef.current = document.activeElement as HTMLElement | null;
    dialogRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      openerRef.current?.focus?.();
    };
  }, [open, close]);

  // Register the video box (when shown) so PlaybackEngine pins the YouTube frame over it; drop it on close/unmount.
  const slotRef = useCallback((el: HTMLDivElement | null) => setSlot(el), [setSlot]);
  useEffect(() => () => setSlot(null), [setSlot]);

  const [result, setResult] = useState<LyricsResult | null>(null);
  const trackId = current?.id;
  useEffect(() => {
    if (!open || !trackId) return;
    let stale = false;
    getLyrics(trackId)
      .then((lyrics) => !stale && setResult({ trackId, status: "ready", lyrics }))
      .catch(() => !stale && setResult({ trackId, status: "error" }));
    return () => {
      stale = true;
    };
  }, [open, trackId]);
  // A result for another track (or none yet) means this one is still loading.
  const lyricsState: LyricsResult | { status: "loading" } =
    result && result.trackId === trackId ? result : { status: "loading" };

  if (!open || !current) return null;

  const lyrics = lyricsState.status === "ready" ? lyricsState.lyrics : null;
  const hasText = !!lyrics && !lyrics.instrumental && (!!lyrics.synced?.length || !!lyrics.plain);
  // The text column exists while we're loading (so the layout doesn't jump when it lands) and when
  // there is text; a confirmed "nothing" / instrumental centers the artwork alone.
  const showTextColumn = lyricsState.status !== "ready" || hasText;

  return (
    <div
      ref={dialogRef}
      role="dialog"
      aria-modal="true"
      aria-label={t("nowPlaying")}
      tabIndex={-1}
      className="fixed inset-0 z-[35] flex flex-col bg-background text-foreground outline-none"
    >
      {/* Backdrop: the hero gradient over a heavily blurred, darkened copy of the artwork. */}
      <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
        <ArtworkImage
          artwork={current.artwork}
          alt=""
          seed={current.id}
          sizes="100vw"
          className="absolute inset-0 size-full scale-125 rounded-none opacity-30 blur-3xl saturate-150"
        />
        <div className="absolute inset-0 bg-gradient-hero opacity-80" />
        <div className="absolute inset-0 bg-background/40" />
      </div>

      {/* Header */}
      <header className="relative flex h-16 shrink-0 items-center justify-between px-4 sm:px-8">
        <p className="text-[11px] font-semibold tracking-[0.2em] text-muted-foreground uppercase">{t("nowPlaying")}</p>
        <button
          type="button"
          onClick={close}
          aria-label={t("closeFullscreen")}
          className="grid size-10 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-hover hover:text-foreground"
        >
          <ChevronDown className="size-6" />
        </button>
      </header>

      {/* Body */}
      <div
        className={cn(
          "relative grid min-h-0 flex-1 gap-8 px-4 pb-4 sm:px-8 lg:gap-16 lg:px-16",
          showTextColumn ? "grid-rows-[auto_minmax(0,1fr)] lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:grid-rows-1" : "grid-rows-1",
        )}
      >
        {/* Surface + meta */}
        <div className={cn("flex min-h-0 flex-col items-center justify-center gap-5 lg:items-stretch", !showTextColumn && "mx-auto w-full max-w-[min(52vh,560px)] lg:items-center")}>
          <div
            className={cn(
              "relative w-full shrink-0",
              showTextColumn ? "max-w-[min(28vh,220px)] lg:max-w-[min(46vh,520px)]" : "max-w-[min(52vh,560px)]",
            )}
          >
            {video ? (
              <div
                ref={slotRef}
                role="img"
                aria-label={current.title}
                className="aspect-square w-full rounded-xl bg-black shadow-[0_40px_80px_-24px_rgba(0,0,0,0.9)]"
              />
            ) : (
              <ArtworkImage
                artwork={current.artwork}
                alt={current.album.title}
                seed={current.id}
                sizes="520px"
                priority
                className="aspect-square w-full rounded-xl shadow-[0_40px_80px_-24px_rgba(0,0,0,0.9)]"
              />
            )}
          </div>

          <div className={cn("w-full min-w-0", showTextColumn ? "max-w-[min(28vh,220px)] text-center lg:max-w-[min(46vh,520px)] lg:text-left" : "text-center")}>
            <Link href={routes.track(current.id)} className="block truncate text-xl font-bold tracking-tight hover:underline sm:text-2xl lg:text-3xl">
              {current.title}
            </Link>
            <p className="mt-1 truncate text-sm text-muted-foreground sm:text-base">
              {current.artists.map((artist, i) => (
                <span key={artist.id}>
                  {i > 0 && ", "}
                  <Link href={routes.artist(artist.id)} className="hover:text-foreground hover:underline">
                    {artist.name}
                  </Link>
                </span>
              ))}
            </p>
            <p className="mt-0.5 truncate text-xs text-subtle-foreground sm:text-sm">
              <Link href={routes.album(current.album.id)} className="hover:underline">
                {current.album.title}
              </Link>
              {current.album.releaseYear && <> · {current.album.releaseYear}</>}
            </p>

            {/* Artwork / video switch — same flag as the Now Playing panel. */}
            <div
              role="group"
              aria-label={panel("surface")}
              className="mt-4 inline-flex gap-1 rounded-lg bg-elevated/60 p-1"
            >
              {(
                [
                  { id: false, key: "surfaceArtwork", icon: ImageIcon },
                  { id: true, key: "surfaceVideo", icon: Clapperboard },
                ] as const
              ).map(({ id, key, icon: Icon }) => (
                <button
                  key={String(id)}
                  type="button"
                  aria-pressed={video === id}
                  onClick={() => setVideo(id)}
                  className={cn(
                    "flex items-center gap-2 rounded-md px-3 py-1.5 text-xs font-medium transition-colors",
                    video === id ? "bg-active text-foreground" : "text-muted-foreground hover:bg-hover hover:text-foreground",
                  )}
                >
                  <Icon className="size-3.5" />
                  {panel(key)}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Lyrics */}
        {showTextColumn && (
          <div className="flex min-h-0 flex-col">
            {lyricsState.status === "loading" && <LyricsSkeleton />}
            {lyricsState.status === "error" && <LyricsNotice icon={MicVocal}>{t("lyricsError")}</LyricsNotice>}
            {lyricsState.status === "ready" && lyrics && hasText && (
              <>
                <LyricsView lyrics={lyrics} className="min-h-0 flex-1" />
                <p className="shrink-0 pt-2 text-[11px] text-subtle-foreground">{t("lyricsBy", { provider: providerLabel(lyrics.provider) })}</p>
              </>
            )}
          </div>
        )}
      </div>

      {/* Instrumental / nothing found — one quiet line under the artwork instead of an empty column. */}
      {lyricsState.status === "ready" && !hasText && (
        <p className="relative shrink-0 pb-2 text-center text-sm text-muted-foreground">
          {lyrics?.instrumental ? t("instrumental") : t("lyricsUnavailable")}
        </p>
      )}

      {/* Transport */}
      <footer className="relative mx-auto flex w-full max-w-3xl shrink-0 flex-col items-center gap-3 px-4 pt-2 pb-6 sm:px-8">
        <PlaybackControls size="lg" />
        <ProgressBar />
      </footer>
    </div>
  );
}

function LyricsSkeleton() {
  return (
    <div aria-hidden className="flex flex-col gap-5 pt-[20vh]">
      {[72, 56, 64, 40, 68, 52].map((w, i) => (
        <span key={i} className="h-7 animate-pulse rounded-md bg-foreground/10 sm:h-9" style={{ width: `${w}%` }} />
      ))}
    </div>
  );
}

function LyricsNotice({ icon: Icon, children }: { icon: typeof MicVocal; children: React.ReactNode }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center text-muted-foreground">
      <span className="grid size-12 place-items-center rounded-full bg-elevated">
        <Icon className="size-5" strokeWidth={1.5} />
      </span>
      <p className="text-sm">{children}</p>
    </div>
  );
}

/** Display names for the provider codes the backend stores; unknown ones fall back to the code itself. */
function providerLabel(provider: string): string {
  switch (provider) {
    case "LRCLIB":
      return "LRCLIB";
    case "MUSIXMATCH":
      return "Musixmatch";
    default:
      return provider;
  }
}

"use client";

import { useCallback, useEffect, useMemo } from "react";
import { Clapperboard, Heart, ImageIcon, Music2, Share2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { joinArtists } from "@/lib/utils/format";
import { Button } from "@/components/ui/button";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { cn } from "@/lib/utils";
import { useUiStore } from "@/lib/store/ui-store";
import { getUpNext, usePlayerStore } from "../store/player-store";
import { useVideoSurfaceStore } from "../store/video-surface-store";

/** Right-panel "Now Playing" tab. Big artwork, meta, source badge, up next. */
export function NowPlayingPanel() {
  const t = useTranslations("panel");
  const common = useTranslations("common");
  const current = usePlayerStore((s) => s.current);
  const queue = usePlayerStore((s) => s.queue);
  const upNext = useMemo(() => getUpNext(queue, current), [queue, current]);
  const play = usePlayerStore((s) => s.play);
  const video = useUiStore((s) => s.nowPlayingVideo);
  const setVideo = useUiStore((s) => s.setNowPlayingVideo);
  const setSlot = useVideoSurfaceStore((s) => s.setSlot);

  // The video box only exists while "video" is chosen; register it so PlaybackEngine can pin the
  // (single, app-wide) YouTube frame over it, and unregister the moment it goes away.
  const slotRef = useCallback((el: HTMLDivElement | null) => setSlot(el), [setSlot]);
  useEffect(() => () => setSlot(null), [setSlot]);

  if (!current) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
        <span className="grid size-14 place-items-center rounded-full bg-elevated">
          <Music2 className="size-6 text-muted-foreground" strokeWidth={1.5} />
        </span>
        <p className="text-sm font-medium">{t("nothingPlaying")}</p>
        <p className="text-xs text-muted-foreground">{t("nothingPlayingHint")}</p>
      </div>
    );
  }

  const next = upNext[0];

  return (
    <div className="flex flex-col gap-5 p-4">
      <div className="relative">
        {video ? (
          <div
            ref={slotRef}
            role="img"
            aria-label={current.title}
            className="aspect-square w-full rounded-lg bg-black shadow-[0_24px_60px_-20px_rgba(0,0,0,0.8)]"
          />
        ) : (
          <ArtworkImage
            artwork={current.artwork}
            alt={current.album.title}
            seed={current.id}
            sizes="320px"
            priority
            className="aspect-square w-full rounded-lg shadow-[0_24px_60px_-20px_rgba(0,0,0,0.8)]"
          />
        )}
      </div>

      {/* Artwork / video switch — the video is the greyscale, non-interactive YouTube frame. */}
      <div role="group" aria-label={t("surface")} className="flex gap-1 rounded-lg bg-elevated/60 p-1">
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
              "flex flex-1 items-center justify-center gap-2 rounded-md py-1.5 text-xs font-medium transition-colors",
              video === id ? "bg-active text-foreground" : "text-muted-foreground hover:bg-hover hover:text-foreground",
            )}
          >
            <Icon className="size-3.5" />
            {t(key)}
          </button>
        ))}
      </div>

      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <Link href={routes.track(current.id)} className="block truncate text-lg font-semibold hover:underline">
            {current.title}
          </Link>
          <p className="truncate text-sm text-muted-foreground">
            {current.artists.map((artist, i) => (
              <span key={artist.id}>
                {i > 0 && ", "}
                <Link href={routes.artist(artist.id)} className="hover:text-foreground hover:underline">
                  {artist.name}
                </Link>
              </span>
            ))}
          </p>
        </div>
        <Button variant="ghost" size="icon" aria-label={common("like")}>
          <Heart className="text-muted-foreground" />
        </Button>
        <Button variant="ghost" size="icon" aria-label={common("share")}>
          <Share2 className="text-muted-foreground" />
        </Button>
      </div>

      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 rounded-lg border border-border bg-elevated/40 p-3 text-xs">
        <dt className="text-subtle-foreground">{t("album")}</dt>
        <dd className="truncate">
          <Link href={routes.album(current.album.id)} className="hover:underline">
            {current.album.title}
          </Link>
          {current.album.releaseYear && <span className="text-muted-foreground"> · {current.album.releaseYear}</span>}
        </dd>
        <dt className="text-subtle-foreground">{t("source")}</dt>
        <dd className="flex items-center gap-1.5">
          <span className="size-1.5 rounded-full bg-success" />
          {t("sourceVerified")}
        </dd>
      </dl>

      {next && (
        <section>
          <h3 className="mb-2 text-[11px] font-semibold tracking-[0.14em] uppercase text-subtle-foreground">
            {t("upNext")}
          </h3>
          <button
            type="button"
            onClick={() => play(next)}
            className="flex w-full items-center gap-3 rounded-md p-2 text-left transition-colors hover:bg-hover"
          >
            <ArtworkImage artwork={next.artwork} alt="" seed={next.id} sizes="40px" className="size-10 shrink-0" />
            <span className="min-w-0">
              <span className="block truncate text-sm font-medium">{next.title}</span>
              <span className="block truncate text-xs text-muted-foreground">{joinArtists(next.artists)}</span>
            </span>
          </button>
        </section>
      )}
    </div>
  );
}

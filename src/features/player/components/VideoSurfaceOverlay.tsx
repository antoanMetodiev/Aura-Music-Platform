"use client";

import { Pause } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { joinArtists } from "@/lib/utils/format";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { usePlayerStore } from "../store/player-store";

/**
 * Our own monochrome layer over the (non-interactive, greyscale) YouTube frame: a film-grain
 * vignette, the track meta, and — whenever the player isn't actually playing — a dimmed cover so
 * none of YouTube's paused/buffering chrome is ever visible. Pointer events stay off: the video
 * can't be clicked, scrubbed or hovered; every control lives in our transport UI.
 */
export function VideoSurfaceOverlay() {
  const t = useTranslations("panel");
  const current = usePlayerStore((s) => s.current);
  const engineState = usePlayerStore((s) => s.engineState);
  const showing = engineState === "playing";

  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 select-none text-white">
      {/* Vignette + subtle grain, always on: reads as "our" video, not an embed. */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,transparent_45%,rgba(0,0,0,0.55)_100%)]" />
      <div className="absolute inset-0 opacity-[0.07] mix-blend-overlay [background-image:repeating-linear-gradient(0deg,#fff_0px,#fff_1px,transparent_1px,transparent_3px)]" />

      {/* Opaque cover for every non-playing state: the (greyscale) artwork stands in for the frame, so
          YouTube's own paused / cued / buffering chrome can never show through. */}
      <div
        className={cn(
          "absolute inset-0 grid place-items-center bg-black transition-opacity duration-300",
          showing ? "opacity-0" : "opacity-100",
        )}
      >
        {current && (
          <ArtworkImage artwork={current.artwork} alt="" seed={current.id} sizes="320px" className="absolute inset-0 rounded-none opacity-50" />
        )}
        {engineState === "paused" && (
          <span className="relative grid size-14 place-items-center rounded-full border border-white/40 bg-black/40">
            <Pause className="size-6 fill-white" />
          </span>
        )}
        {engineState === "buffering" && (
          <span className="relative size-8 animate-spin rounded-full border-2 border-white/30 border-t-white" />
        )}
      </div>

      {/* Bottom meta strip. */}
      <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 via-black/40 to-transparent px-4 pt-12 pb-3">
        <p className="mb-1 text-[10px] font-semibold tracking-[0.2em] text-white/60 uppercase">{t("videoBadge")}</p>
        {current && (
          <>
            <p className="truncate text-base leading-tight font-semibold">{current.title}</p>
            <p className="truncate text-xs text-white/70">{joinArtists(current.artists)}</p>
          </>
        )}
      </div>
    </div>
  );
}

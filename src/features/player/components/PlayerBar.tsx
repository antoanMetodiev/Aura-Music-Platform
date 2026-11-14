"use client";

import { AudioLines, Heart, ListMusic, Maximize2, MicVocal, Users } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";
import { type RightPanelTab, useUiStore } from "@/lib/store/ui-store";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { usePlayerStore } from "../store/player-store";
import { PlaybackControls } from "./PlaybackControls";
import { ProgressBar } from "./ProgressBar";
import { VolumeControl } from "./VolumeControl";

/** Persistent desktop player (≥ md). Full width, below the three panels. */
export function PlayerBar() {
  const t = useTranslations("player");
  const current = usePlayerStore((s) => s.current);
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const openRightPanel = useUiStore((s) => s.openRightPanel);
  const rightPanelOpen = useUiStore((s) => s.rightPanelOpen);
  const rightPanelTab = useUiStore((s) => s.rightPanelTab);
  const fullscreenOpen = useUiStore((s) => s.fullscreenPlayerOpen);
  const setFullscreenOpen = useUiStore((s) => s.setFullscreenPlayerOpen);

  const panelActive = (tab: RightPanelTab) => rightPanelOpen && rightPanelTab === tab;

  return (
    <footer
      className={cn(
        "relative hidden h-[88px] shrink-0 grid-cols-[1fr_auto_1fr] items-center gap-4 px-4 md:grid",
        "border-t border-border bg-background",
      )}
    >
      {/* Ambient glow while playing */}
      <span
        aria-hidden
        className={cn(
          "pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/60 to-transparent transition-opacity duration-700",
          isPlaying ? "opacity-100" : "opacity-0",
        )}
      />

      {/* Left: now playing */}
      <div className="flex min-w-0 items-center gap-3">
        {current ? (
          <>
            <Link href={routes.album(current.album.id)} className="shrink-0">
              <ArtworkImage
                artwork={current.artwork}
                alt={current.album.title}
                seed={current.id}
                sizes="56px"
                className="size-14 shadow-lg"
              />
            </Link>
            <div className="min-w-0">
              <Link href={routes.track(current.id)} className="block truncate text-sm font-medium hover:underline">
                {current.title}
              </Link>
              <p className="truncate text-xs text-muted-foreground">
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
            <Tooltip>
              <TooltipTrigger
                render={
                  <button
                    type="button"
                    aria-label={t("saveToLiked")}
                    className="ml-1 grid size-8 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors hover:text-foreground"
                  />
                }
              >
                <Heart className="size-4" />
              </TooltipTrigger>
              <TooltipContent>{t("saveToLiked")}</TooltipContent>
            </Tooltip>
          </>
        ) : (
          <div className="flex items-center gap-3 text-muted-foreground">
            <span className="grid size-14 place-items-center rounded-md bg-elevated">
              <AudioLines className="size-5" strokeWidth={1.5} />
            </span>
            <span className="text-sm">{t("nothingPlaying")}</span>
          </div>
        )}
      </div>

      {/* Center: controls + progress */}
      <div className="flex w-[min(40vw,560px)] flex-col items-center gap-1.5">
        <PlaybackControls />
        <ProgressBar />
      </div>

      {/* Right: extras */}
      <div className="flex items-center justify-end gap-1">
        <PanelButton label={t("lyrics")} active={fullscreenOpen} disabled={!current} onClick={() => setFullscreenOpen(!fullscreenOpen)}>
          <MicVocal className="size-[18px]" />
        </PanelButton>
        <PanelButton label={t("queue")} active={panelActive("queue")} onClick={() => openRightPanel("queue")}>
          <ListMusic className="size-[18px]" />
        </PanelButton>
        <PanelButton label={t("friends")} active={panelActive("friends")} onClick={() => openRightPanel("friends")}>
          <Users className="size-[18px]" />
        </PanelButton>
        <VolumeControl className="ml-2" />
        <PanelButton label={t("fullscreen")} active={fullscreenOpen} disabled={!current} onClick={() => setFullscreenOpen(!fullscreenOpen)}>
          <Maximize2 className="size-4" />
        </PanelButton>
      </div>
    </footer>
  );
}

function PanelButton({
  label,
  active,
  disabled,
  onClick,
  children,
}: {
  label: string;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <Tooltip>
      <TooltipTrigger
        render={
          <button
            type="button"
            aria-label={label}
            aria-pressed={active}
            disabled={disabled}
            onClick={onClick}
            className={cn(
              "relative hidden size-8 place-items-center rounded-full text-muted-foreground transition-colors hover:text-foreground disabled:opacity-40 disabled:hover:text-muted-foreground xl:grid",
              active && "text-primary-hover hover:text-primary-hover",
            )}
          />
        }
      >
        {children}
        {active && <span className="absolute bottom-0.5 size-1 rounded-full bg-primary-hover" />}
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
}

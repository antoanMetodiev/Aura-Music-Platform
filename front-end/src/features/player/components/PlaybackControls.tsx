"use client";

import { Pause, Play, Repeat, Repeat1, Shuffle, SkipBack, SkipForward } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { usePlayerStore } from "../store/player-store";

export function PlaybackControls({ size = "default" }: { size?: "default" | "lg" }) {
  const t = useTranslations("player");
  const isPlaying = usePlayerStore((s) => s.isPlaying);
  const hasTrack = usePlayerStore((s) => s.current !== null);
  const shuffle = usePlayerStore((s) => s.shuffle);
  const repeat = usePlayerStore((s) => s.repeat);
  const { toggle, next, previous, toggleShuffle, cycleRepeat } = usePlayerStore.getState();

  const lg = size === "lg";

  return (
    <div className={cn("flex items-center", lg ? "gap-6" : "gap-2")}>
      <ControlButton label={shuffle ? t("shuffleOff") : t("shuffleOn")} active={shuffle} onClick={toggleShuffle}>
        <Shuffle className={lg ? "size-5" : "size-4"} />
      </ControlButton>

      <ControlButton label={t("previous")} onClick={previous} disabled={!hasTrack}>
        <SkipBack className={cn("fill-current", lg ? "size-6" : "size-[18px]")} />
      </ControlButton>

      <button
        type="button"
        aria-label={isPlaying ? t("pause") : t("play")}
        onClick={toggle}
        disabled={!hasTrack}
        className={cn(
          "grid shrink-0 place-items-center rounded-full bg-foreground text-background transition-transform",
          "hover:scale-105 active:scale-95 disabled:opacity-40 disabled:hover:scale-100",
          lg ? "size-16" : "size-9",
        )}
      >
        {isPlaying ? (
          <Pause className={cn("fill-current", lg ? "size-7" : "size-4")} />
        ) : (
          <Play className={cn("translate-x-px fill-current", lg ? "size-7" : "size-4")} />
        )}
      </button>

      <ControlButton label={t("next")} onClick={next} disabled={!hasTrack}>
        <SkipForward className={cn("fill-current", lg ? "size-6" : "size-[18px]")} />
      </ControlButton>

      <ControlButton
        label={repeat === "off" ? t("repeatOn") : repeat === "all" ? t("repeatOne") : t("repeatOff")}
        active={repeat !== "off"}
        onClick={cycleRepeat}
      >
        {repeat === "one" ? (
          <Repeat1 className={lg ? "size-5" : "size-4"} />
        ) : (
          <Repeat className={lg ? "size-5" : "size-4"} />
        )}
      </ControlButton>
    </div>
  );
}

function ControlButton({
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
              "relative grid size-8 place-items-center rounded-full text-muted-foreground transition-colors",
              "hover:text-foreground disabled:opacity-40 disabled:hover:text-muted-foreground",
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

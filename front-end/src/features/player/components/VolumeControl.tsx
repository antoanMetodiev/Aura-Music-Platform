"use client";

import { Volume, Volume1, Volume2, VolumeX } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { Slider } from "@/components/ui/slider";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { usePlayerStore } from "../store/player-store";

export function VolumeControl({ className }: { className?: string }) {
  const t = useTranslations("player");
  const volume = usePlayerStore((s) => s.volume);
  const muted = usePlayerStore((s) => s.muted);
  const setVolume = usePlayerStore((s) => s.setVolume);
  const toggleMute = usePlayerStore((s) => s.toggleMute);

  const effective = muted ? 0 : volume;
  const Icon = effective === 0 ? VolumeX : effective < 0.34 ? Volume : effective < 0.67 ? Volume1 : Volume2;

  return (
    <div className={cn("group/volume flex items-center gap-2", className)}>
      <Tooltip>
        <TooltipTrigger
          render={
            <button
              type="button"
              aria-label={muted ? t("unmute") : t("mute")}
              onClick={toggleMute}
              className="grid size-8 place-items-center rounded-full text-muted-foreground transition-colors hover:text-foreground"
            />
          }
        >
          <Icon className="size-[18px]" />
        </TooltipTrigger>
        <TooltipContent>{muted ? t("unmute") : t("mute")}</TooltipContent>
      </Tooltip>
      {/* Fixed-width wrapper: the slider root is `w-full` and would collapse inside a flex row. */}
      <div className="w-24 shrink-0">
        <Slider
          aria-label={t("volume")}
          min={0}
          max={100}
          step={1}
          value={[Math.round(effective * 100)]}
          onValueChange={(value) => setVolume((Array.isArray(value) ? value[0]! : value) / 100)}
          className={cn(
            "**:data-[slot=slider-track]:bg-border-strong",
            "**:data-[slot=slider-range]:bg-foreground/90 group-hover/volume:**:data-[slot=slider-range]:bg-primary",
            "**:data-[slot=slider-thumb]:size-3 **:data-[slot=slider-thumb]:opacity-0 group-hover/volume:**:data-[slot=slider-thumb]:opacity-100 focus-within:**:data-[slot=slider-thumb]:opacity-100",
          )}
        />
      </div>
    </div>
  );
}

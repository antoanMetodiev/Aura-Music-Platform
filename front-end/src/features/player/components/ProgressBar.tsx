"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { formatDuration } from "@/lib/utils/format";
import { Slider } from "@/components/ui/slider";
import { usePlayerStore } from "../store/player-store";

/** Seekable progress with mono timestamps. Thumb only appears on hover, like a real player. */
export function ProgressBar({ className, showTimes = true }: { className?: string; showTimes?: boolean }) {
  const t = useTranslations("player");
  const positionMs = usePlayerStore((s) => s.positionMs);
  const durationMs = usePlayerStore((s) => s.current?.durationMs ?? 0);
  const seek = usePlayerStore((s) => s.seek);
  const disabled = durationMs === 0;

  return (
    <div className={cn("flex w-full items-center gap-2", className)}>
      {showTimes && (
        <span className="w-10 shrink-0 text-right font-mono text-[11px] tabular-nums text-muted-foreground">
          {disabled ? "-:--" : formatDuration(positionMs)}
        </span>
      )}
      <Slider
        aria-label={t("seek")}
        min={0}
        max={Math.max(durationMs, 1)}
        step={1000}
        value={[Math.min(positionMs, durationMs)]}
        disabled={disabled}
        onValueChange={(value) => seek(Array.isArray(value) ? value[0]! : value)}
        className={cn(
          "group/progress",
          "**:data-[slot=slider-track]:bg-border-strong",
          "**:data-[slot=slider-range]:transition-colors **:data-[slot=slider-range]:bg-foreground/90 hover:**:data-[slot=slider-range]:bg-primary",
          "**:data-[slot=slider-thumb]:size-3 **:data-[slot=slider-thumb]:opacity-0 **:data-[slot=slider-thumb]:transition-opacity hover:**:data-[slot=slider-thumb]:opacity-100 focus-within:**:data-[slot=slider-thumb]:opacity-100",
        )}
      />
      {showTimes && (
        <span className="w-10 shrink-0 font-mono text-[11px] tabular-nums text-muted-foreground">
          {disabled ? "-:--" : formatDuration(durationMs)}
        </span>
      )}
    </div>
  );
}

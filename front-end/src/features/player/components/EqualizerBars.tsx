import { cn } from "@/lib/utils";

/** Three animated bars — the universal "this one is playing" glyph. */
export function EqualizerBars({ playing, className }: { playing: boolean; className?: string }) {
  return (
    <span aria-hidden className={cn("flex h-3.5 items-end gap-[2px]", className)}>
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className={cn("w-[3px] rounded-[1px] bg-primary-hover", playing ? "animate-eq" : "h-1")}
          style={playing ? { animationDelay: `${i * 0.18}s` } : undefined}
        />
      ))}
    </span>
  );
}

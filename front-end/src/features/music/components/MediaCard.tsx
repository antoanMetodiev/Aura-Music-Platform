import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import type { Artwork, Track } from "@/types/catalog";
import { PlayButton } from "./PlayButton";

export interface MediaCardProps {
  href: string;
  title: string;
  subtitle?: string;
  artwork?: Artwork;
  /** Stable seed for the fallback gradient. */
  seed: string;
  shape?: "square" | "circle";
  /** Small label rendered on the artwork (e.g. "Daily Mix 1"). */
  badge?: string;
  /** Tracks the hover play button starts. Omit to hide the button. */
  tracks?: Track[];
  className?: string;
}

/**
 * The card used in every horizontal rail (albums, playlists, artists, mixes).
 * Whole card is a link; the play button floats over the artwork on hover.
 */
export function MediaCard({
  href,
  title,
  subtitle,
  artwork,
  seed,
  shape = "square",
  badge,
  tracks,
  className,
}: MediaCardProps) {
  return (
    <article
      className={cn(
        "group relative flex w-40 shrink-0 snap-start flex-col gap-3 rounded-lg p-3 transition-colors hover:bg-hover sm:w-44",
        className,
      )}
    >
      <Link href={href} className="absolute inset-0 z-0 rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={title} />

      <div className="relative">
        <ArtworkImage
          artwork={artwork}
          alt=""
          seed={seed}
          shape={shape}
          sizes="176px"
          className={cn(
            "aspect-square w-full shadow-[0_10px_30px_-12px_rgba(0,0,0,0.7)] transition-transform duration-300",
            shape === "square" && "group-hover:-translate-y-0.5",
          )}
        />
        {badge && (
          <span className="pointer-events-none absolute right-2 bottom-2 left-2 rounded-sm bg-black/60 px-2 py-1 text-[11px] font-semibold tracking-wide text-white uppercase backdrop-blur-sm">
            {badge}
          </span>
        )}
        {tracks && tracks.length > 0 && (
          <PlayButton
            tracks={tracks}
            contextId={seed}
            size="md"
            className={cn(
              "absolute right-2 bottom-2 z-10 translate-y-2 opacity-0 transition-all duration-200",
              "group-hover:translate-y-0 group-hover:opacity-100 focus-visible:translate-y-0 focus-visible:opacity-100",
              shape === "circle" && "right-1 bottom-1",
            )}
          />
        )}
      </div>

      <div className={cn("min-w-0", shape === "circle" && "text-center")}>
        <h3 className="truncate text-sm font-medium text-foreground">{title}</h3>
        {subtitle && <p className="mt-0.5 line-clamp-2 text-xs leading-snug text-muted-foreground">{subtitle}</p>}
      </div>
    </article>
  );
}

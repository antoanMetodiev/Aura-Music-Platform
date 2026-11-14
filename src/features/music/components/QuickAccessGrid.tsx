import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import type { Artwork, Track } from "@/types/catalog";
import { PlayButton } from "./PlayButton";

export interface QuickAccessItem {
  id: string;
  href: string;
  title: string;
  artwork?: Artwork;
  tracks?: Track[];
  /** Renders the Liked Songs style gradient tile instead of artwork. */
  variant?: "default" | "liked";
}

/**
 * The compact 2×N grid of pills at the top of Home — the user's most-reached-for
 * items. Each pill is a link; play floats in on hover.
 */
export function QuickAccessGrid({ items, className }: { items: QuickAccessItem[]; className?: string }) {
  return (
    <ul className={cn("grid grid-cols-2 gap-2 2xl:grid-cols-4", className)}>
      {items.map((item) => (
        <li key={item.id} className="group relative flex h-12 items-center overflow-hidden rounded-md bg-elevated/70 transition-colors hover:bg-hover sm:h-14">
          <Link href={item.href} className="absolute inset-0 z-0 outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring" aria-label={item.title} />
          {item.variant === "liked" ? (
            <span className="grid size-12 shrink-0 place-items-center bg-gradient-to-br sm:size-14 from-[#4c82ff] via-[#2f6bff] to-[#1e3f8f]">
              <svg viewBox="0 0 24 24" className="size-5 fill-white" aria-hidden>
                <path d="M12 21s-7-4.6-9.3-8.6C.9 9.3 2.3 5.5 5.9 5.1c2-.2 3.5.8 4.4 2 .9-1.2 2.4-2.2 4.4-2 3.6.4 5 4.2 3.2 7.3C19 16.4 12 21 12 21z" />
              </svg>
            </span>
          ) : (
            <ArtworkImage artwork={item.artwork} alt="" seed={item.id} sizes="56px" className="size-12 shrink-0 rounded-none sm:size-14" />
          )}
          <span className="min-w-0 flex-1 truncate px-2.5 text-xs font-semibold sm:px-3 sm:text-sm">{item.title}</span>
          {item.tracks && item.tracks.length > 0 && (
            <PlayButton
              tracks={item.tracks}
              contextId={item.id}
              size="sm"
              className="z-10 mr-2 opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100"
            />
          )}
        </li>
      ))}
    </ul>
  );
}

import { BadgeCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import type { Artist } from "@/types/catalog";

/**
 * Artist page header: the artist's photo bleeds across the top as a dimmed backdrop, with the
 * round portrait and a very large name in front — the play button lives with the track list.
 */
export function ArtistHero({ artist }: { artist: Artist }) {
  const t = useTranslations("artist");

  return (
    <header className="relative -mx-3 -mt-6 overflow-hidden sm:-mx-5 sm:-mt-10">
      {artist.artwork && (
        <div aria-hidden className="absolute inset-0">
          <ArtworkImage artwork={artist.artwork} alt="" seed={artist.id} sizes="100vw" priority className="size-full rounded-none opacity-40 blur-2xl" />
          <div className="absolute inset-0 bg-gradient-to-b from-black/10 via-black/40 to-background" />
        </div>
      )}

      <div className="relative flex flex-col items-center gap-6 px-6 pt-14 pb-8 text-center sm:flex-row sm:items-end sm:px-8 sm:pt-20 sm:text-left">
        <ArtworkImage
          artwork={artist.artwork}
          alt={artist.name}
          seed={artist.id}
          shape="circle"
          sizes="(min-width: 640px) 208px, 160px"
          priority
          className="size-40 shrink-0 shadow-[0_24px_60px_-16px_rgba(0,0,0,0.9)] ring-4 ring-black/30 sm:size-52"
        />
        <div className="min-w-0">
          <p className="flex items-center justify-center gap-1.5 text-xs font-semibold tracking-[0.14em] text-white/70 uppercase sm:justify-start">
            <BadgeCheck className="size-4 text-primary-hover" />
            {t("eyebrow")}
          </p>
          <h1 className="mt-2 line-clamp-2 text-4xl font-black tracking-tight text-balance sm:text-6xl lg:text-7xl">{artist.name}</h1>
        </div>
      </div>
    </header>
  );
}

import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import type { Album } from "@/types/catalog";

/** Album page header: cover, type · year, title, artist. The play button lives with the tracklist. */
export function AlbumHero({ album }: { album: Album }) {
  const t = useTranslations("album");
  const common = useTranslations("common");
  const eyebrow = [t(`types.${album.albumType}`), album.releaseYear].filter(Boolean).join(" · ");

  return (
    <header className="flex flex-col items-center gap-6 px-3 text-center sm:flex-row sm:items-end sm:text-left">
      <ArtworkImage
        artwork={album.artwork}
        alt={album.title}
        seed={album.id}
        sizes="(min-width: 640px) 224px, 192px"
        priority
        className="size-48 shrink-0 shadow-[0_24px_60px_-20px_rgba(0,0,0,0.9)] sm:size-56"
      />

      <div className="flex min-w-0 flex-col gap-3">
        <p className="text-xs font-semibold tracking-[0.14em] text-subtle-foreground uppercase">{eyebrow}</p>
        <h1 className="line-clamp-2 text-3xl font-bold tracking-tight text-balance sm:text-5xl">
          {album.explicit && (
            <span
              className="mr-2 inline-grid size-5 place-items-center rounded-[3px] bg-muted-foreground/80 align-[3px] text-[11px] font-bold text-background sm:align-[8px]"
              aria-label={common("explicit")}
            >
              E
            </span>
          )}
          {album.title}
        </h1>
        <Link
          href={routes.artist(album.artist.id)}
          className="inline-flex items-center gap-2 self-center text-sm font-medium hover:underline sm:self-start"
        >
          <ArtworkImage artwork={album.artist.artwork} alt="" seed={album.artist.id} shape="circle" sizes="24px" className="size-6" />
          {album.artist.name}
        </Link>
      </div>
    </header>
  );
}

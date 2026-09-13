import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { joinArtists } from "@/lib/utils/format";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { PlayButton } from "@/features/music/components/PlayButton";
import type { ArtistSummary, Track } from "@/types/catalog";

export type TopResult =
  | { kind: "track"; track: Track; context: Track[] }
  | { kind: "artist"; artist: ArtistSummary };

/** The large highlighted best match at the top of "All" search results. */
export function TopResultCard({ result }: { result: TopResult }) {
  const t = useTranslations("search");
  const home = useTranslations("home");

  if (result.kind === "artist") {
    const { artist } = result;
    return (
      <Link
        href={routes.artist(artist.id)}
        className="group block rounded-lg border border-border bg-elevated/40 p-5 transition-colors hover:border-border-strong hover:bg-elevated"
      >
        <p className="mb-3 text-xs font-semibold tracking-[0.14em] text-subtle-foreground uppercase">{t("topResult")}</p>
        <ArtworkImage
          artwork={artist.artwork}
          alt={artist.name}
          seed={artist.id}
          shape="circle"
          sizes="96px"
          className="size-24 shadow-[0_10px_30px_-12px_rgba(0,0,0,0.7)]"
        />
        <h3 className="mt-4 truncate text-2xl font-bold tracking-tight">{artist.name}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{home("cards.artist")}</p>
      </Link>
    );
  }

  const { track, context } = result;
  return (
    <div className="group relative rounded-lg border border-border bg-elevated/40 p-5 transition-colors hover:border-border-strong hover:bg-elevated">
      <p className="mb-3 text-xs font-semibold tracking-[0.14em] text-subtle-foreground uppercase">{t("topResult")}</p>
      <Link href={routes.track(track.id)} className="absolute inset-0 z-0 rounded-lg" aria-label={track.title} />
      <ArtworkImage
        artwork={track.artwork}
        alt=""
        seed={track.id}
        sizes="96px"
        className="size-24 shadow-[0_10px_30px_-12px_rgba(0,0,0,0.7)]"
      />
      <h3 className="mt-4 truncate text-2xl font-bold tracking-tight">{track.title}</h3>
      <p className="mt-1 truncate text-sm text-muted-foreground">{joinArtists(track.artists)}</p>
      <PlayButton
        tracks={context.length > 0 ? context : [track]}
        contextId={track.id}
        size="lg"
        className="relative z-10 mt-4"
      />
    </div>
  );
}

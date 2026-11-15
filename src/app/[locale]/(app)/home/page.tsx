import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { routes } from "@/config/routes";
import { Link } from "@/i18n/navigation";
import { getSession } from "@/lib/auth/session";
import { joinArtists } from "@/lib/utils/format";
import { getHomeFeed, type HomeFeed } from "@/features/home/api/homeApi";
import { GreetingHeader } from "@/features/home/components/GreetingHeader";
import { HorizontalSection } from "@/features/music/components/HorizontalSection";
import { MediaCard } from "@/features/music/components/MediaCard";
import type { ArtistSummary, Track } from "@/types/catalog";

export async function generateMetadata({ params }: PageProps<"/[locale]/home">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "home" });
  return { title: t("title") };
}

/** Section keys recommendation-svc sends → their heading. Unknown keys are skipped, not guessed at. */
const SECTION_TITLES = {
  trending: "sections.popularNow",
  discover: "sections.discover",
} as const;

const MAX_ARTISTS = 12;
const MAX_GENRES = 16;

/**
 * Home — the signed-out feed from recommendation-svc (`/recommendations/home`): popular tracks,
 * discovery picks, the artists behind them and the catalog's genres. Personal sections (recently
 * played, made for you, friends) come back once library-svc and socials-svc exist.
 */
export default async function HomePage({ params }: PageProps<"/[locale]/home">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations("home");
  // Guests get the greeting without a name.
  const displayName = (await getSession())?.user.name ?? null;

  let feed: HomeFeed | null = null;
  try {
    feed = await getHomeFeed();
  } catch {
    // Render's free tier can be asleep or a service down — the page still renders, with a notice.
  }

  const sections = withoutRepeats(
    (feed?.sections ?? []).filter((s): s is { key: keyof typeof SECTION_TITLES; tracks: Track[] } => s.key in SECTION_TITLES),
  );
  const artists = uniqueArtists(sections.flatMap((s) => s.tracks)).slice(0, MAX_ARTISTS);
  const genres = (feed?.genres ?? []).slice(0, MAX_GENRES);

  return (
    <div className="relative">
      {/* Hero gradient bleeding down from under the top bar */}
      <div aria-hidden className="pointer-events-none absolute inset-x-0 -top-16 h-[420px] bg-gradient-hero opacity-90" />

      <div className="relative flex flex-col gap-10 px-3 pt-4 pb-12 sm:px-5 sm:pt-6">
        <div className="px-3">
          <GreetingHeader name={displayName} />
        </div>

        {sections.length === 0 && (
          <p className="mx-3 rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
            {t("unavailable")}
          </p>
        )}

        {sections.map(({ key, tracks }) => (
          <HorizontalSection key={key} title={t(SECTION_TITLES[key])}>
            {tracks.map((track, index) => (
              <MediaCard
                key={track.id}
                href={routes.track(track.id)}
                title={track.title}
                subtitle={joinArtists(track.artists)}
                artwork={track.artwork ?? track.album.artwork}
                seed={track.id}
                // Play from this card onwards, like a list.
                tracks={tracks.slice(index)}
              />
            ))}
          </HorizontalSection>
        ))}

        {artists.length > 0 && (
          <HorizontalSection title={t("sections.artists")}>
            {artists.map((artist) => (
              <MediaCard
                key={artist.id}
                href={routes.artist(artist.id)}
                title={artist.name}
                subtitle={t("cards.artist")}
                artwork={artist.artwork}
                seed={artist.id}
                shape="circle"
              />
            ))}
          </HorizontalSection>
        )}

        {genres.length > 0 && (
          <section className="px-3">
            <h2 className="mb-3 text-xl font-semibold tracking-tight sm:text-2xl">{t("sections.genres")}</h2>
            <ul className="flex flex-wrap gap-2">
              {genres.map((genre) => (
                <li key={genre}>
                  <Link
                    href={routes.search(genre)}
                    className="inline-flex rounded-full border border-border bg-elevated/40 px-4 py-1.5 text-sm capitalize transition-colors hover:bg-hover"
                  >
                    {genre}
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </div>
  );
}

/** The service's sections overlap (discover can repeat trending) — each track shows once, in the first section. */
function withoutRepeats<S extends { tracks: Track[] }>(sections: S[]): S[] {
  const seen = new Set<string>();
  return sections
    .map((s) => ({ ...s, tracks: s.tracks.filter((t) => !seen.has(t.id) && (seen.add(t.id), true)) }))
    .filter((s) => s.tracks.length > 0);
}

function uniqueArtists(tracks: Track[]): ArtistSummary[] {
  const seen = new Map<string, ArtistSummary>();
  for (const track of tracks) {
    const artist = track.artists[0];
    if (artist && !seen.has(artist.id)) seen.set(artist.id, artist);
  }
  return [...seen.values()];
}

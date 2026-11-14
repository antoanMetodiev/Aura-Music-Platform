import { cache, Suspense } from "react";
import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Disc3 } from "lucide-react";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { routes } from "@/config/routes";
import { ApiError } from "@/lib/api/client";
import { EmptyState } from "@/components/common/EmptyState";
import { getArtist, getArtistAbout, getArtistAlbums, getArtistDiscographyStatus, getArtistTopTracks } from "@/features/music/api/catalogApi";
import { ArtistAboutSection } from "@/features/music/components/ArtistAboutSection";
import { ArtistHero } from "@/features/music/components/ArtistHero";
import { ArtistTopTracks } from "@/features/music/components/ArtistTopTracks";
import { CatalogSyncNotice } from "@/features/music/components/CatalogSyncNotice";
import { HorizontalSection } from "@/features/music/components/HorizontalSection";
import { MediaCard } from "@/features/music/components/MediaCard";
import { TrackListSkeleton } from "@/features/music/components/TrackListSkeleton";
import { MediaRailSkeleton } from "@/features/search/components/skeletons";
import type { Artist } from "@/types/catalog";

const loadArtist = cache(async (id: string): Promise<Artist> => {
  try {
    return await getArtist(id);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) notFound();
    throw error;
  }
});

export async function generateMetadata({ params }: PageProps<"/[locale]/artists/[artistId]">): Promise<Metadata> {
  const { artistId } = (await params) as { artistId: string };
  const artist = await loadArtist(artistId);
  return { title: artist.name };
}

/**
 * Every read here is local, so the whole page renders at once — even for an artist the background
 * sync has never touched. That artist simply has less to show, and {@link CatalogSyncNotice} says so
 * while the backend fetches the rest and refreshes the page when it lands.
 */
export default async function ArtistPage({ params }: PageProps<"/[locale]/artists/[artistId]">) {
  const { locale, artistId } = (await params) as { locale: Locale; artistId: string };
  setRequestLocale(locale);
  const artist = await loadArtist(artistId);
  // A provider duplicate of an artist resolves to the canonical one (the backend returns its id) —
  // land on that URL so the page has one address, however the user got here.
  if (artist.id !== artistId) redirect(routes.artist(artist.id));

  // Local read; tells us whether the backend is still filling this artist's catalogue in.
  const discography = await getArtistDiscographyStatus(artist.id).catch(() => null);

  return (
    <div className="flex flex-col gap-10 px-3 pt-6 pb-12 sm:px-5 sm:pt-10">
      <ArtistHero artist={artist} />
      {discography && !discography.complete && <CatalogSyncNotice artistId={artist.id} />}
      <Suspense fallback={<TrackListSkeleton rows={5} />}>
        <TopTracks artist={artist} />
      </Suspense>
      <Suspense fallback={<MediaRailSkeleton />}>
        <Discography artist={artist} />
      </Suspense>
      <Suspense fallback={<AboutSkeleton />}>
        <About artist={artist} locale={locale} />
      </Suspense>
    </div>
  );
}

async function TopTracks({ artist }: { artist: Artist }) {
  const t = await getTranslations("artist");
  const tracks = await getArtistTopTracks(artist.id, 10).catch(() => null);
  if (tracks === null) {
    return <EmptyState icon={Disc3} title={t("tracksUnavailable.title")} description={t("tracksUnavailable.description")} className="min-h-[30vh]" />;
  }
  if (tracks.length === 0) {
    return <EmptyState icon={Disc3} title={t("noTracks")} className="min-h-[30vh]" />;
  }
  return <ArtistTopTracks artistId={artist.id} tracks={tracks} />;
}

/**
 * Biography, tags, links and similar artists — gathered by the backend from Last.fm/Discogs on the
 * first open (a couple of seconds, once), then cached. Missing or unreachable = the section simply isn't there.
 */
async function About({ artist, locale }: { artist: Artist; locale: Locale }) {
  const about = await getArtistAbout(artist.id, locale).catch(() => null);
  if (!about) return null;
  return <ArtistAboutSection about={about} />;
}

function AboutSkeleton() {
  return (
    <div aria-hidden className="grid gap-8 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <div className="flex flex-col gap-3">
        <span className="h-7 w-40 animate-pulse rounded-md bg-foreground/10" />
        {[100, 96, 92, 98, 60].map((w, i) => (
          <span key={i} className="h-4 animate-pulse rounded bg-foreground/10" style={{ width: `${w}%` }} />
        ))}
      </div>
      <span className="h-40 animate-pulse rounded-xl bg-foreground/10" />
    </div>
  );
}

async function Discography({ artist }: { artist: Artist }) {
  const t = await getTranslations("artist");
  const albums = await getArtistAlbums(artist.id).catch(() => []);
  if (albums.length === 0) return null;
  return (
    <HorizontalSection title={t("discography")}>
      {albums.map((album) => (
        <MediaCard
          key={album.id}
          href={routes.album(album.id)}
          title={album.title}
          subtitle={[album.releaseYear, t(`types.${album.albumType}`)].filter(Boolean).join(" · ")}
          artwork={album.artwork}
          seed={album.id}
        />
      ))}
    </HorizontalSection>
  );
}

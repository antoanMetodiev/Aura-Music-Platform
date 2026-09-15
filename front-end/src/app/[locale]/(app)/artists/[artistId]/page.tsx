import { cache, Suspense } from "react";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Disc3 } from "lucide-react";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { routes } from "@/config/routes";
import { ApiError } from "@/lib/api/client";
import { EmptyState } from "@/components/common/EmptyState";
import { getArtist, getArtistAlbums, getArtistTopTracks } from "@/features/music/api/catalogApi";
import { ArtistHero } from "@/features/music/components/ArtistHero";
import { ArtistTopTracks } from "@/features/music/components/ArtistTopTracks";
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
 * The artist itself is a local read, so the hero renders at once; the popular tracks and the
 * discography stream in below. The first open of an artist the background sync hasn't reached yet
 * pulls their whole discography from the provider — a few seconds, once.
 */
export default async function ArtistPage({ params }: PageProps<"/[locale]/artists/[artistId]">) {
  const { locale, artistId } = (await params) as { locale: Locale; artistId: string };
  setRequestLocale(locale);
  const artist = await loadArtist(artistId);

  return (
    <div className="flex flex-col gap-10 px-3 pt-6 pb-12 sm:px-5 sm:pt-10">
      <ArtistHero artist={artist} />
      <Suspense fallback={<TrackListSkeleton rows={5} />}>
        <TopTracks artist={artist} />
      </Suspense>
      <Suspense fallback={<MediaRailSkeleton />}>
        <Discography artist={artist} />
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

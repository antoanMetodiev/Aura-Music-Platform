import { cache, Suspense } from "react";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Disc3 } from "lucide-react";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { ApiError } from "@/lib/api/client";
import { EmptyState } from "@/components/common/EmptyState";
import { getAlbum, getAlbumTracks } from "@/features/music/api/catalogApi";
import { AlbumHero } from "@/features/music/components/AlbumHero";
import { AlbumTrackList } from "@/features/music/components/AlbumTrackList";
import { TrackListSkeleton } from "@/features/music/components/TrackListSkeleton";
import type { Album } from "@/types/catalog";

// One backend call per request even though both generateMetadata and the page need the album.
const loadAlbum = cache(async (id: string): Promise<Album> => {
  try {
    return await getAlbum(id);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) notFound();
    throw error;
  }
});

export async function generateMetadata({ params }: PageProps<"/[locale]/albums/[albumId]">): Promise<Metadata> {
  const { albumId } = (await params) as { albumId: string };
  const album = await loadAlbum(albumId);
  return { title: `${album.title} · ${album.artist.name}` };
}

/**
 * The album itself is a local read (it was persisted when the user found it), so the hero renders
 * immediately; the tracklist streams in below — the first open of an album pulls it from the
 * provider, every later open is served from our own catalog.
 */
export default async function AlbumPage({ params }: PageProps<"/[locale]/albums/[albumId]">) {
  const { locale, albumId } = (await params) as { locale: Locale; albumId: string };
  setRequestLocale(locale);
  const album = await loadAlbum(albumId);

  return (
    <div className="relative">
      <div aria-hidden className="pointer-events-none absolute inset-x-0 -top-16 h-[480px] bg-gradient-hero opacity-90" />

      <div className="relative flex flex-col gap-8 px-3 pt-6 pb-12 sm:px-5 sm:pt-10">
        <AlbumHero album={album} />
        <Suspense fallback={<TrackListSkeleton rows={Math.min(Math.max(album.numberOfTracks, 4), 12)} />}>
          <AlbumTracks album={album} />
        </Suspense>
      </div>
    </div>
  );
}

async function AlbumTracks({ album }: { album: Album }) {
  const t = await getTranslations("album");
  const tracks = await getAlbumTracks(album.id).catch(() => null);

  if (tracks === null) {
    return (
      <EmptyState
        icon={Disc3}
        title={t("tracksUnavailable.title")}
        description={t("tracksUnavailable.description")}
        className="min-h-[30vh]"
      />
    );
  }
  if (tracks.length === 0) {
    return <EmptyState icon={Disc3} title={t("noTracks")} className="min-h-[30vh]" />;
  }
  return <AlbumTrackList albumId={album.id} tracks={tracks} />;
}

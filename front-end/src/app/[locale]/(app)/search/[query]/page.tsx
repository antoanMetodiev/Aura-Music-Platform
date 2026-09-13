import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { AlbumsSection } from "@/features/search/components/AlbumsSection";
import { ArtistsSection } from "@/features/search/components/ArtistsSection";
import { filterFromTypeParam, typeParamFromFilter } from "@/features/search/lib/searchFilter";
import { SearchFilterChips } from "@/features/search/components/SearchFilterChips";
import { MediaGridSkeleton, MediaRailSkeleton, TopResultAndSongsSkeleton, TrackListSkeleton } from "@/features/search/components/skeletons";
import { SongsListSection } from "@/features/search/components/SongsListSection";
import { TopResultAndSongsSection } from "@/features/search/components/TopResultAndSongsSection";

export async function generateMetadata({ params }: PageProps<"/[locale]/search/[query]">): Promise<Metadata> {
  const { locale, query } = (await params) as { locale: Locale; query: string };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: `${t("searchResults")} · ${decodeURIComponent(query)}` };
}

/**
 * Every section below fetches (and streams in via Suspense) independently — the page shell and
 * filter chips render immediately, and each section pops in the moment ITS OWN `catalog-svc` call
 * resolves, instead of the whole page waiting on the slowest of tracks/albums/artists.
 */
export default async function SearchResultsPage({
  params,
  searchParams,
}: PageProps<"/[locale]/search/[query]">) {
  const { locale, query: rawQuery } = (await params) as { locale: Locale; query: string };
  setRequestLocale(locale);

  const query = decodeURIComponent(rawQuery);
  const typeParam = (await searchParams)?.type;
  const filter = filterFromTypeParam(Array.isArray(typeParam) ? typeParam[0] : typeParam);
  const type = typeParamFromFilter(filter);

  return (
    <div className="flex flex-col gap-6 px-3 py-6 sm:px-5">
      <SearchFilterChips active={filter} />

      {filter === "all" && (
        <div className="flex flex-col gap-8">
          <Suspense fallback={<TopResultAndSongsSkeleton />}>
            <TopResultAndSongsSection query={query} />
          </Suspense>
          <Suspense fallback={<MediaRailSkeleton />}>
            <AlbumsSection query={query} limit={10} variant="rail" />
          </Suspense>
          <Suspense fallback={<MediaRailSkeleton shape="circle" />}>
            <ArtistsSection query={query} limit={10} variant="rail" />
          </Suspense>
        </div>
      )}

      {type === "tracks" && (
        <Suspense fallback={<TrackListSkeleton />} key={query}>
          <SongsListSection query={query} limit={24} showEmptyState />
        </Suspense>
      )}

      {type === "albums" && (
        <Suspense fallback={<MediaGridSkeleton />} key={query}>
          <AlbumsSection query={query} limit={24} variant="grid" showEmptyState />
        </Suspense>
      )}

      {type === "artists" && (
        <Suspense fallback={<MediaGridSkeleton shape="circle" />} key={query}>
          <ArtistsSection query={query} limit={24} variant="grid" showEmptyState />
        </Suspense>
      )}
    </div>
  );
}

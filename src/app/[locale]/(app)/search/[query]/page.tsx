import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { ArtistsSection } from "@/features/search/components/ArtistsSection";
import { filterFromTypeParam, typeParamFromFilter } from "@/features/search/lib/searchFilter";
import { SearchFilterChips } from "@/features/search/components/SearchFilterChips";
import { SongsListSection } from "@/features/search/components/SongsListSection";
import { TopResultAndSongsSection } from "@/features/search/components/TopResultAndSongsSection";

export async function generateMetadata({ params }: PageProps<"/[locale]/search/[query]">): Promise<Metadata> {
  const { locale, query } = (await params) as { locale: Locale; query: string };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: `${t("searchResults")} · ${decodeURIComponent(query)}` };
}

/**
 * The page shell and filter chips render on the server immediately; every section below is a
 * client component that fires two `catalog-svc` calls at once — our own catalog (instant) and the
 * provider-backed search — and paints local matches first (see `useProgressiveSearch`). Sections
 * are independent: each pops in / updates the moment its own calls resolve.
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
          <TopResultAndSongsSection query={query} />
          <ArtistsSection query={query} limit={10} variant="rail" />
        </div>
      )}

      {type === "tracks" && <SongsListSection query={query} limit={60} showEmptyState />}
      {type === "artists" && <ArtistsSection query={query} limit={24} variant="grid" showEmptyState />}
    </div>
  );
}

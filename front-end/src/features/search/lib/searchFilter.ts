import type { CatalogSearchType } from "@/features/music/api/catalogApi";

/**
 * Plain helpers shared by the server page and the client filter chips. Kept in a file WITHOUT
 * "use client" — Next.js treats every export of a client-directive file as a client reference, so a
 * Server Component can't call a function from one directly (RSC boundary), even a pure one.
 */
export const SEARCH_FILTERS = ["all", "songs", "albums", "artists"] as const;
export type SearchFilter = (typeof SEARCH_FILTERS)[number];

const FILTER_TYPE: Record<SearchFilter, CatalogSearchType | undefined> = {
  all: undefined,
  songs: "tracks",
  albums: "albums",
  artists: "artists",
};

/** Maps the page's `?type=` search param back to which chip is active. */
export function filterFromTypeParam(type: string | undefined): SearchFilter {
  const entry = (Object.entries(FILTER_TYPE) as [SearchFilter, CatalogSearchType | undefined][]).find(
    ([, value]) => value === type,
  );
  return entry?.[0] ?? "all";
}

export function typeParamFromFilter(filter: SearchFilter): CatalogSearchType | undefined {
  return FILTER_TYPE[filter];
}

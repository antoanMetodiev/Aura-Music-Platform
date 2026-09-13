"use client";

import { useTranslations } from "next-intl";
import { Link, usePathname } from "@/i18n/navigation";
import { cn } from "@/lib/utils";
import { SEARCH_FILTERS, type SearchFilter } from "../lib/searchFilter";

const FILTER_TYPE_PARAM: Record<SearchFilter, string | undefined> = {
  all: undefined,
  songs: "tracks",
  albums: "albums",
  artists: "artists",
};

/** All / Songs / Albums / Artists — updates the `type` search param on the current results page. */
export function SearchFilterChips({ active }: { active: SearchFilter }) {
  const t = useTranslations("search");
  const pathname = usePathname();

  return (
    <div className="scrollbar-none flex gap-2 overflow-x-auto">
      {SEARCH_FILTERS.map((filter) => {
        const typeParam = FILTER_TYPE_PARAM[filter];
        const isActive = filter === active;
        return (
          <Link
            key={filter}
            href={typeParam ? { pathname, query: { type: typeParam } } : pathname}
            className={cn(
              "shrink-0 rounded-full px-4 py-1.5 text-sm font-medium transition-colors",
              isActive ? "bg-foreground text-background" : "bg-elevated text-foreground hover:bg-hover",
            )}
          >
            {t(`filters.${filter}`)}
          </Link>
        );
      })}
    </div>
  );
}

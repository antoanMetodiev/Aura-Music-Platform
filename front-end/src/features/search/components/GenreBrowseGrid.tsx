import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";

/**
 * Canned genre searches for the empty-search browse state (Spotify's "Browse all" grid,
 * FRONTEND.md §1.1). Not a real genre taxonomy from the catalog — each tile is just a pre-picked
 * search term that happens to surface that genre well. Query stays in English (matches TIDAL
 * content regardless of UI language); only the tile label is translated.
 */
const GENRES = [
  { key: "pop", query: "pop", gradient: "from-[#2f6bff] to-[#0b2a5b]" },
  { key: "hipHop", query: "hip hop", gradient: "from-[#7a3b2e] to-[#2b120c]" },
  { key: "rock", query: "rock", gradient: "from-[#4a2a5c] to-[#160a1e]" },
  { key: "electronic", query: "electronic", gradient: "from-[#1e6b6b] to-[#062626]" },
  { key: "indie", query: "indie", gradient: "from-[#3b3b5c] to-[#0e0e1e]" },
  { key: "latino", query: "latino", gradient: "from-[#8a5a1e] to-[#2b1a06]" },
  { key: "rnb", query: "r&b", gradient: "from-[#6b1e4a] to-[#26081b]" },
  { key: "chill", query: "chill", gradient: "from-[#1e4a6b] to-[#061826]" },
] as const;

export function GenreBrowseGrid() {
  const t = useTranslations("search");

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
      {GENRES.map((genre) => (
        <Link
          key={genre.key}
          href={routes.search(genre.query)}
          className={`relative flex h-28 items-start overflow-hidden rounded-lg bg-gradient-to-br p-4 transition-transform hover:-translate-y-0.5 ${genre.gradient}`}
        >
          <span className="text-lg font-bold text-white drop-shadow-sm">{t(`genres.${genre.key}`)}</span>
        </Link>
      ))}
    </div>
  );
}

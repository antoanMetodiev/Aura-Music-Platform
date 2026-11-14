"use client";

import { ArrowRight, Play, SearchX } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { formatDuration, joinArtists } from "@/lib/utils/format";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { Skeleton } from "@/components/common/Skeleton";
import type { ArtistSummary, Track } from "@/types/catalog";
import type { SuggestionsState } from "../hooks/useSuggestions";

/** One selectable row of the dropdown, in the order the arrow keys walk them. */
export type SuggestionItem =
  | { kind: "artist"; id: string; artist: ArtistSummary }
  | { kind: "track"; id: string; track: Track }
  | { kind: "see-all"; id: "see-all" };

export function flattenSuggestions(state: SuggestionsState, query: string): SuggestionItem[] {
  const items: SuggestionItem[] = [];
  if (state.data) {
    for (const artist of state.data.artists) items.push({ kind: "artist", id: `artist:${artist.id}`, artist });
    for (const track of state.data.tracks) items.push({ kind: "track", id: `track:${track.id}`, track });
  }
  if (query.trim()) items.push({ kind: "see-all", id: "see-all" });
  return items;
}

interface SearchSuggestionsProps {
  state: SuggestionsState;
  query: string;
  items: SuggestionItem[];
  activeIndex: number;
  listboxId: string;
  onHover: (index: number) => void;
  onSelect: (item: SuggestionItem) => void;
}

/**
 * The panel under the search box: artists (round) first, then tracks, then "see all results".
 * Purely presentational — keyboard state and navigation live in `GlobalSearchInput`.
 */
export function SearchSuggestions({ state, query, items, activeIndex, listboxId, onHover, onSelect }: SearchSuggestionsProps) {
  const t = useTranslations("search");
  const hasResults = !!state.data && (state.data.artists.length > 0 || state.data.tracks.length > 0);
  const showSkeleton = state.loading && !state.data;
  const showEmpty = !state.loading && state.data !== null && !hasResults;

  return (
    <div
      className={cn(
        "absolute inset-x-0 top-full z-50 mt-2 overflow-hidden rounded-xl border border-border bg-elevated shadow-[0_24px_60px_-12px_rgba(0,0,0,0.85)]",
        "animate-in fade-in-0 slide-in-from-top-1 duration-150",
      )}
    >
      <div className="flex items-center justify-between px-4 pt-3 pb-1">
        <span className="text-[11px] font-semibold tracking-[0.14em] text-subtle-foreground uppercase">{t("suggestions.label")}</span>
        {state.loading && state.data && (
          <span className="flex items-center gap-1.5 text-[11px] text-subtle-foreground">
            <span className="size-2.5 animate-spin rounded-full border border-subtle-foreground/40 border-t-subtle-foreground" />
            {t("suggestions.searching")}
          </span>
        )}
      </div>

      <ul id={listboxId} role="listbox" aria-label={t("suggestions.label")} className={cn("flex flex-col p-2 pt-1", state.loading && state.data && "opacity-80")}>
        {showSkeleton && Array.from({ length: 5 }).map((_, i) => <SkeletonRow key={i} round={i === 0} />)}

        {showEmpty && (
          <li className="flex items-center gap-3 px-3 py-4 text-sm text-muted-foreground">
            <SearchX className="size-4 shrink-0" />
            <span>{t("suggestions.empty", { query: state.query })}</span>
          </li>
        )}

        {items.map((item, index) => {
          const active = index === activeIndex;
          const id = `${listboxId}-${index}`;
          const rowClass = cn(
            "flex w-full cursor-pointer items-center gap-3 rounded-lg px-2 py-2 text-left transition-colors",
            active ? "bg-hover" : "hover:bg-hover/60",
          );
          if (item.kind === "artist") {
            return (
              <li key={item.id} id={id} role="option" aria-selected={active}>
                <button type="button" className={rowClass} onMouseEnter={() => onHover(index)} onClick={() => onSelect(item)}>
                  <ArtworkImage artwork={item.artist.artwork} alt="" seed={item.artist.id} shape="circle" sizes="44px" className="size-11 shrink-0" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold">{highlight(item.artist.name, query)}</span>
                    <span className="block truncate text-xs text-muted-foreground">{t("suggestions.artist")}</span>
                  </span>
                  <ArrowRight className={cn("size-4 shrink-0 text-subtle-foreground transition-opacity", active ? "opacity-100" : "opacity-0")} />
                </button>
              </li>
            );
          }
          if (item.kind === "track") {
            return (
              <li key={item.id} id={id} role="option" aria-selected={active}>
                <button type="button" className={rowClass} onMouseEnter={() => onHover(index)} onClick={() => onSelect(item)}>
                  <span className="relative shrink-0">
                    <ArtworkImage artwork={item.track.artwork} alt="" seed={item.track.id} sizes="44px" className="size-11" />
                    <span
                      className={cn(
                        "absolute inset-0 grid place-items-center rounded-md bg-black/55 transition-opacity",
                        active ? "opacity-100" : "opacity-0",
                      )}
                    >
                      <Play className="size-4 fill-white text-white" />
                    </span>
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{highlight(item.track.title, query)}</span>
                    <span className="block truncate text-xs text-muted-foreground">
                      {t("suggestions.song")} · {joinArtists(item.track.artists)}
                    </span>
                  </span>
                  <span className="shrink-0 font-mono text-[11px] tabular-nums text-subtle-foreground">{formatDuration(item.track.durationMs)}</span>
                </button>
              </li>
            );
          }
          return (
            <li key={item.id} id={id} role="option" aria-selected={active} className={cn(hasResults && "mt-1 border-t border-border pt-1")}>
              <button type="button" className={cn(rowClass, "text-sm text-muted-foreground hover:text-foreground", active && "text-foreground")} onMouseEnter={() => onHover(index)} onClick={() => onSelect(item)}>
                <span className="grid size-11 shrink-0 place-items-center rounded-md bg-background/60">
                  <ArrowRight className="size-4" />
                </span>
                <span className="truncate">{t("suggestions.seeAll", { query: query.trim() })}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function SkeletonRow({ round }: { round: boolean }) {
  return (
    <li className="flex items-center gap-3 px-2 py-2">
      <Skeleton className={cn("size-11 shrink-0", round && "rounded-full")} />
      <span className="flex min-w-0 flex-1 flex-col gap-1.5">
        <Skeleton className="h-3.5 w-2/5" />
        <Skeleton className="h-3 w-1/4" />
      </span>
    </li>
  );
}

/** Bolds the part of `text` that matches the query (first occurrence, case-insensitive). */
function highlight(text: string, query: string): React.ReactNode {
  const q = query.trim();
  if (!q) return text;
  const index = text.toLowerCase().indexOf(q.toLowerCase());
  if (index === -1) return text;
  return (
    <>
      {text.slice(0, index)}
      <span className="text-foreground underline decoration-primary/60 decoration-2 underline-offset-2">{text.slice(index, index + q.length)}</span>
      {text.slice(index + q.length)}
    </>
  );
}

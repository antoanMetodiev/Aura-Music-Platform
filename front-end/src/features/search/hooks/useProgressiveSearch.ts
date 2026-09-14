"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import type { SearchOptions } from "@/features/music/api/catalogApi";

export type SearchPhase =
  /** Neither call has answered yet. */
  | "loading"
  /** Painted from our own catalog; the provider call is still in flight. */
  | "local"
  /** The provider (or its cache) answered — this is the final list. */
  | "done"
  /** The provider failed but the local catalog had matches — those stay on screen. */
  | "local-only";

export interface ProgressiveSearchState<T> {
  items: T[];
  phase: SearchPhase;
  /** Set only when nothing could be shown at all. */
  error: ApiError | null;
}

const LOADING: ProgressiveSearchState<never> = { items: [], phase: "loading", error: null };

/**
 * Two requests per section, fired together (Project-Info.md §31): `localOnly` answers from our
 * own catalog in a few hundred ms and is painted immediately; the full call (cache → TIDAL) lands
 * later and replaces it. If the full call is already cached it usually wins the race outright, in
 * which case the late local answer is simply dropped — the full list is always the final word.
 */
export function useProgressiveSearch<T>(
  query: string,
  fetcher: (query: string, options: SearchOptions) => Promise<T[]>,
): ProgressiveSearchState<T> {
  // State is tagged with the query it belongs to, so a new query reads as "loading" on the very
  // render it arrives instead of flashing the previous query's results.
  const [state, setState] = useState<{ query: string } & ProgressiveSearchState<T>>({ query, ...LOADING });

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;

    fetcher(query, { localOnly: true, signal })
      .then((items) => {
        if (signal.aborted || items.length === 0) return;
        setState((current) =>
          current.query === query && current.phase !== "loading" ? current : { query, items, phase: "local", error: null },
        );
      })
      .catch(() => {
        /* local is best-effort — the full call decides what the user ends up seeing */
      });

    fetcher(query, { signal })
      .then((items) => {
        if (signal.aborted) return;
        setState({ query, items, phase: "done", error: null });
      })
      .catch((error: unknown) => {
        if (signal.aborted) return;
        setState((current) =>
          current.query === query && current.items.length > 0
            ? { ...current, phase: "local-only" }
            : {
                query,
                items: [],
                phase: "done",
                error: error instanceof ApiError ? error : new ApiError("Search failed", { code: "UNKNOWN_ERROR", cause: error }),
              },
        );
      });

    return () => controller.abort();
  }, [query, fetcher]);

  return state.query === query ? state : LOADING;
}

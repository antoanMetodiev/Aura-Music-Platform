"use client";

import { useEffect, useState } from "react";
import { suggest, type Suggestions } from "@/features/music/api/catalogApi";

export const SUGGEST_MIN_CHARS = 2;
const DEBOUNCE_MS = 180;

export interface SuggestionsState {
  /** Results for the query they were fetched for — kept on screen while the next keystroke's request is in flight. */
  data: Suggestions | null;
  /** True from the first keystroke until the latest request settles. */
  loading: boolean;
  /** The query `data` belongs to. */
  query: string;
}

const NONE: SuggestionsState = { data: null, loading: false, query: "" };

/**
 * Debounced type-ahead against the local catalog. Every keystroke cancels the previous in-flight
 * request; results are cached per query for the session so backspacing through a word is free.
 * Everything visible is derived from (current query, cache, last fetch) — the effect only schedules
 * network calls and records their results.
 */
export function useSuggestions(query: string): SuggestionsState {
  const trimmed = query.trim();
  const key = trimmed.toLowerCase();
  const [fetched, setFetched] = useState<{ query: string; data: Suggestions } | null>(null);
  // A per-instance map, created once; read during render (a ref may not be), mutated only from the fetch.
  const [cache] = useState(() => new Map<string, Suggestions>());

  useEffect(() => {
    if (trimmed.length < SUGGEST_MIN_CHARS || cache.has(key)) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      suggest(trimmed, controller.signal)
        .then((data) => {
          if (controller.signal.aborted) return;
          cache.set(key, data);
          setFetched({ query: trimmed, data });
        })
        .catch(() => {
          if (controller.signal.aborted) return;
          setFetched({ query: trimmed, data: { tracks: [], artists: [] } });
        });
    }, DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [trimmed, key, cache]);

  if (trimmed.length < SUGGEST_MIN_CHARS) return NONE;
  const cached = cache.get(key);
  if (cached) return { data: cached, loading: false, query: trimmed };
  // Not cached yet: show whatever the previous query produced, flagged as loading.
  return { data: fetched?.data ?? null, loading: true, query: fetched?.query ?? "" };
}

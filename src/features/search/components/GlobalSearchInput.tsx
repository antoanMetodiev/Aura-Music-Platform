"use client";

import { useEffect, useId, useMemo, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";
import { usePlayerStore } from "@/features/player/store/player-store";
import { SUGGEST_MIN_CHARS, useSuggestions } from "../hooks/useSuggestions";
import { flattenSuggestions, SearchSuggestions, type SuggestionItem } from "./SearchSuggestions";

interface GlobalSearchInputProps {
  className?: string;
  initialValue?: string;
}

/**
 * Top-bar search box with Spotify-style type-ahead: from two characters on, a panel under the box
 * lists matching artists and tracks from our own catalog (debounced, cancellable, cached per query).
 * ↑/↓ walk the rows, Enter picks the highlighted one (or, with nothing highlighted, runs the full
 * search), Esc closes. Picking a track plays it; picking an artist opens their page. `/` focuses
 * the box from anywhere.
 */
export function GlobalSearchInput({ className, initialValue = "" }: GlobalSearchInputProps) {
  const t = useTranslations("search");
  const router = useRouter();
  const inputRef = useRef<HTMLInputElement>(null);
  const rootRef = useRef<HTMLFormElement>(null);
  const listboxId = useId();
  const [value, setValue] = useState(initialValue);
  const [open, setOpen] = useState(false);
  const [rawActiveIndex, setActiveIndex] = useState(-1);
  const play = usePlayerStore((s) => s.play);

  const suggestions = useSuggestions(open ? value : "");
  const items = useMemo(() => flattenSuggestions(suggestions, value), [suggestions, value]);
  const panelVisible = open && value.trim().length >= SUGGEST_MIN_CHARS;
  // Typing resets the cursor (see onChange); a list that shrank underneath it just drops it.
  const activeIndex = rawActiveIndex < items.length ? rawActiveIndex : -1;

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "/" || event.metaKey || event.ctrlKey || event.altKey) return;
      const target = event.target as HTMLElement | null;
      const typing = target?.tagName === "INPUT" || target?.tagName === "TEXTAREA" || target?.isContentEditable;
      if (typing) return;
      event.preventDefault();
      inputRef.current?.focus();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const close = () => {
    setOpen(false);
    setActiveIndex(-1);
  };

  // Click anywhere outside closes the panel.
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) close();
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  const runFullSearch = () => {
    const query = value.trim();
    close();
    inputRef.current?.blur();
    router.push(query ? routes.search(query) : routes.search());
  };

  const select = (item: SuggestionItem) => {
    if (item.kind === "see-all") return runFullSearch();
    close();
    inputRef.current?.blur();
    if (item.kind === "track") {
      const tracks = suggestions.data?.tracks ?? [item.track];
      play(item.track, tracks);
      return;
    }
    router.push(routes.artist(item.artist.id));
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Escape") {
      if (open) {
        event.preventDefault();
        close();
      }
      return;
    }
    if (!panelVisible || items.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((i) => (i + 1) % items.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((i) => (i <= 0 ? items.length - 1 : i - 1));
    } else if (event.key === "Enter" && activeIndex >= 0) {
      event.preventDefault();
      select(items[activeIndex]!);
    }
  };

  return (
    <form
      ref={rootRef}
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        runFullSearch();
      }}
      className={cn("group relative", className)}
    >
      <Search className="pointer-events-none absolute top-1/2 left-3.5 z-10 size-4 -translate-y-1/2 text-muted-foreground transition-colors group-focus-within:text-foreground" />
      <input
        ref={inputRef}
        type="search"
        role="combobox"
        aria-expanded={panelVisible}
        aria-controls={listboxId}
        aria-autocomplete="list"
        aria-activedescendant={activeIndex >= 0 ? `${listboxId}-${activeIndex}` : undefined}
        value={value}
        onChange={(e) => {
          setValue(e.target.value);
          setActiveIndex(-1);
          setOpen(true);
        }}
        onFocus={() => {
          setActiveIndex(-1);
          setOpen(true);
        }}
        onKeyDown={onKeyDown}
        placeholder={t("placeholder")}
        aria-label={t("label")}
        autoComplete="off"
        spellCheck={false}
        className={cn(
          "h-10 w-full rounded-full border border-transparent bg-elevated pr-16 pl-10 text-sm text-foreground outline-none transition-all",
          "placeholder:text-muted-foreground hover:bg-hover",
          "focus:border-border-strong focus:bg-hover focus:ring-2 focus:ring-primary/40",
          "[&::-webkit-search-cancel-button]:hidden",
        )}
      />
      <span className="pointer-events-none absolute top-1/2 right-3 z-10 -translate-y-1/2">
        {value ? (
          <button
            type="button"
            onClick={() => {
              setValue("");
              inputRef.current?.focus();
            }}
            aria-label={t("clear")}
            className="pointer-events-auto grid size-6 place-items-center rounded-full text-muted-foreground hover:bg-active hover:text-foreground"
          >
            <X className="size-3.5" />
          </button>
        ) : (
          <kbd className="hidden rounded border border-border-strong bg-background/60 px-1.5 font-mono text-[10px] leading-5 text-subtle-foreground sm:block">
            /
          </kbd>
        )}
      </span>

      {panelVisible && (
        <SearchSuggestions
          state={suggestions}
          query={value}
          items={items}
          activeIndex={activeIndex}
          listboxId={listboxId}
          onHover={setActiveIndex}
          onSelect={select}
        />
      )}
    </form>
  );
}

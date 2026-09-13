"use client";

import { useEffect, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";

interface GlobalSearchInputProps {
  className?: string;
  initialValue?: string;
}

/**
 * Top-bar search box. Submits to /search/[query]; `/` focuses it from anywhere
 * (unless the user is already typing somewhere). Live suggestions come later
 * with the Search feature.
 */
export function GlobalSearchInput({ className, initialValue = "" }: GlobalSearchInputProps) {
  const t = useTranslations("search");
  const router = useRouter();
  const inputRef = useRef<HTMLInputElement>(null);
  const [value, setValue] = useState(initialValue);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "/" || event.metaKey || event.ctrlKey || event.altKey) return;
      const target = event.target as HTMLElement | null;
      const typing =
        target?.tagName === "INPUT" || target?.tagName === "TEXTAREA" || target?.isContentEditable;
      if (typing) return;
      event.preventDefault();
      inputRef.current?.focus();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const query = value.trim();
    router.push(query ? routes.search(query) : routes.search());
  };

  return (
    <form role="search" onSubmit={submit} className={cn("group relative", className)}>
      <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground transition-colors group-focus-within:text-foreground" />
      <input
        ref={inputRef}
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={t("placeholder")}
        aria-label={t("label")}
        autoComplete="off"
        className={cn(
          "h-10 w-full rounded-full border border-transparent bg-elevated pr-16 pl-10 text-sm text-foreground outline-none transition-all",
          "placeholder:text-muted-foreground hover:bg-hover",
          "focus:border-border-strong focus:bg-hover focus:ring-2 focus:ring-primary/40",
          "[&::-webkit-search-cancel-button]:hidden",
        )}
      />
      <span className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2">
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
    </form>
  );
}

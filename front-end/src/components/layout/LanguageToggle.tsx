"use client";

import { useTransition } from "react";
import { useLocale, useTranslations } from "next-intl";
import { usePathname, useRouter } from "@/i18n/navigation";
import { type Locale, locales } from "@/i18n/routing";
import { cn } from "@/lib/utils";

/**
 * Segmented EN | BG switch for the navigation. Swaps only the locale segment of the current URL
 * (/en/home ↔ /bg/home). `compact` drops the caption, `stacked` also puts the codes one above the other (icon rail).
 */
interface LanguageToggleProps {
  className?: string;
  /** Hide the "Language" caption — just the two codes. */
  compact?: boolean;
  /** Codes one above the other (the 68px icon rail). Implies compact. */
  stacked?: boolean;
}

export function LanguageToggle({ className, compact = false, stacked = false }: LanguageToggleProps) {
  const t = useTranslations("nav");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const [pending, startTransition] = useTransition();

  const change = (next: Locale) => {
    if (next === locale) return;
    startTransition(() => {
      router.replace(pathname, { locale: next });
    });
  };

  return (
    <div className={cn("flex items-center gap-2", className)}>
      {!compact && !stacked && <span className="text-xs text-muted-foreground">{t("language")}</span>}
      <div
        role="radiogroup"
        aria-label={t("language")}
        className={cn(
          "flex rounded-full border border-border bg-elevated/60 p-0.5 text-xs font-semibold tracking-wide",
          // Stacked in the 68px icon rail, where two pills side by side would not fit.
          stacked && "flex-col",
        )}
      >
        {locales.map((code) => {
          const active = code === locale;
          return (
            <button
              key={code}
              type="button"
              role="radio"
              aria-checked={active}
              disabled={pending}
              onClick={() => change(code)}
              className={cn(
                "rounded-full px-2.5 py-1 uppercase transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring",
                active ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {code}
            </button>
          );
        })}
      </div>
    </div>
  );
}

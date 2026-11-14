"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

interface HorizontalSectionProps {
  title: string;
  /** Small label above the title, e.g. "Made for Antoan". */
  eyebrow?: string;
  href?: string;
  showAllLabel?: string;
  children: React.ReactNode;
  className?: string;
}

/**
 * A titled rail of cards with ‹ › arrows (TIDAL) and swipe/scroll-snap (Spotify).
 * Arrows disable at the edges. Children should be fixed-width `MediaCard`s.
 */
export function HorizontalSection({
  title,
  eyebrow,
  href,
  showAllLabel,
  children,
  className,
}: HorizontalSectionProps) {
  const t = useTranslations("common");
  const railRef = useRef<HTMLDivElement>(null);
  const [canScroll, setCanScroll] = useState({ left: false, right: false });

  const update = useCallback(() => {
    const el = railRef.current;
    if (!el) return;
    const max = el.scrollWidth - el.clientWidth;
    setCanScroll({ left: el.scrollLeft > 4, right: el.scrollLeft < max - 4 });
  }, []);

  useEffect(() => {
    update();
    const el = railRef.current;
    if (!el) return;
    const observer = new ResizeObserver(update);
    observer.observe(el);
    el.addEventListener("scroll", update, { passive: true });
    return () => {
      observer.disconnect();
      el.removeEventListener("scroll", update);
    };
  }, [update]);

  const scrollBy = (direction: 1 | -1) => {
    const el = railRef.current;
    if (!el) return;
    el.scrollBy({ left: direction * el.clientWidth * 0.8, behavior: "smooth" });
  };

  return (
    <section className={cn("group/section", className)}>
      <header className="mb-2 flex items-end justify-between gap-4 px-3">
        <div className="min-w-0">
          {eyebrow && <p className="text-xs font-medium text-muted-foreground">{eyebrow}</p>}
          {href ? (
            <Link href={href} className="block truncate text-xl font-semibold tracking-tight hover:underline sm:text-2xl">
              {title}
            </Link>
          ) : (
            <h2 className="truncate text-xl font-semibold tracking-tight sm:text-2xl">{title}</h2>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {href && (
            <Link
              href={href}
              className="mr-2 text-xs font-semibold text-muted-foreground transition-colors hover:text-foreground"
            >
              {showAllLabel ?? t("showAll")}
            </Link>
          )}
          <RailButton label={t("scrollLeft")} disabled={!canScroll.left} onClick={() => scrollBy(-1)}>
            <ChevronLeft className="size-4" />
          </RailButton>
          <RailButton label={t("scrollRight")} disabled={!canScroll.right} onClick={() => scrollBy(1)}>
            <ChevronRight className="size-4" />
          </RailButton>
        </div>
      </header>

      <div
        ref={railRef}
        className="scrollbar-none -mx-1 flex snap-x snap-mandatory gap-1 overflow-x-auto scroll-smooth px-1 pb-1"
      >
        {children}
      </div>
    </section>
  );
}

function RailButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string;
  disabled: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "hidden size-8 place-items-center rounded-full border border-border bg-elevated text-muted-foreground transition-all sm:grid",
        "hover:border-border-strong hover:text-foreground disabled:opacity-30 disabled:hover:border-border disabled:hover:text-muted-foreground",
      )}
    >
      {children}
    </button>
  );
}

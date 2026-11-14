"use client";

import { CloudOff, LoaderCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import type { SearchPhase } from "../hooks/useProgressiveSearch";

/** One quiet line under a section while it's showing local results only. Renders nothing otherwise. */
export function SearchPhaseHint({ phase }: { phase: SearchPhase }) {
  const t = useTranslations("search");
  if (phase === "local") {
    return (
      <p className="flex items-center gap-2 px-3 text-xs text-muted-foreground" aria-live="polite">
        <LoaderCircle className="size-3.5 animate-spin" />
        {t("refining")}
      </p>
    );
  }
  if (phase === "local-only") {
    return (
      <p className="flex items-center gap-2 px-3 text-xs text-muted-foreground" aria-live="polite">
        <CloudOff className="size-3.5" />
        {t("localOnly")}
      </p>
    );
  }
  return null;
}

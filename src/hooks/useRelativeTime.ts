import { useNow, useTranslations } from "next-intl";
import { relativeTimeParts } from "@/lib/utils/format";

interface RelativeTimeOptions {
  /**
   * Re-render with a fresh "now" this often (ms) so labels don't go stale on a long-lived screen.
   * Client Components only — next-intl ignores it (and warns) in Server Components, whose output
   * never re-renders anyway.
   */
  updateInterval?: number;
}

/**
 * Returns a formatter for compact relative times ("3m", "2h" / "3м", "2ч").
 * Works in both server and client components (next-intl `useTranslations` does).
 *
 * "Now" comes from next-intl's request-scoped snapshot (i18n/request.ts), not `new Date()` at
 * render time — otherwise the server and the hydrating client compute against different clocks
 * and React reports a text mismatch.
 */
export function useRelativeTime({ updateInterval }: RelativeTimeOptions = {}) {
  const t = useTranslations("time");
  const now = useNow(updateInterval === undefined ? undefined : { updateInterval });
  return (iso: string) => {
    const { key, n } = relativeTimeParts(iso, now);
    return key === "justNow" ? t("justNow") : t(key, { n });
  };
}

import { useTranslations } from "next-intl";
import { relativeTimeParts } from "@/lib/utils/format";

/**
 * Returns a formatter for compact relative times ("3m", "2h" / "3м", "2ч").
 * Works in both server and client components (next-intl `useTranslations` does).
 */
export function useRelativeTime() {
  const t = useTranslations("time");
  return (iso: string) => {
    const { key, n } = relativeTimeParts(iso);
    return key === "justNow" ? t("justNow") : t(key, { n });
  };
}

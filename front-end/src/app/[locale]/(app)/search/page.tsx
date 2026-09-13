import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { GenreBrowseGrid } from "@/features/search/components/GenreBrowseGrid";

export async function generateMetadata({ params }: PageProps<"/[locale]/search">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("search") };
}

/** Empty search state — genre browse grid, same shape as Spotify's "Browse all" (FRONTEND.md §1.1). */
export default async function SearchPage({ params }: PageProps<"/[locale]/search">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations("search");

  return (
    <div className="px-3 py-6 sm:px-5">
      <h1 className="mb-4 text-xl font-semibold tracking-tight sm:text-2xl">{t("browseAll")}</h1>
      <GenreBrowseGrid />
    </div>
  );
}

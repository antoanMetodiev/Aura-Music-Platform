import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { ComingSoon } from "@/components/common/ComingSoon";

export async function generateMetadata({ params }: PageProps<"/[locale]/playlists/[playlistId]">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("playlist") };
}

export default async function Page({ params }: PageProps<"/[locale]/playlists/[playlistId]">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  return <ComingSoon titleKey="playlist" />;
}

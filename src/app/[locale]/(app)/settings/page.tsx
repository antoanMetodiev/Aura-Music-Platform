import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { ComingSoon } from "@/components/common/ComingSoon";

export async function generateMetadata({ params }: PageProps<"/[locale]/settings">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("settings") };
}

export default async function Page({ params }: PageProps<"/[locale]/settings">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  return <ComingSoon titleKey="settings" />;
}

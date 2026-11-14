import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { ComingSoon } from "@/components/common/ComingSoon";

export async function generateMetadata({ params }: PageProps<"/[locale]/friends/requests">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("friendRequests") };
}

export default async function Page({ params }: PageProps<"/[locale]/friends/requests">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  return <ComingSoon titleKey="friendRequests" />;
}

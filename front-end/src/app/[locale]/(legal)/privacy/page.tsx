import type { Metadata } from "next";
import { setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { LegalDocument } from "@/features/legal/components/LegalDocument";
import { privacy } from "@/features/legal/content/privacy";

export async function generateMetadata({ params }: PageProps<"/[locale]/privacy">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  return { title: privacy[locale].title };
}

export default async function Page({ params }: PageProps<"/[locale]/privacy">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  return <LegalDocument content={privacy[locale]} />;
}

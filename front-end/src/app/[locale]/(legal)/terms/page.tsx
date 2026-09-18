import type { Metadata } from "next";
import { setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { LegalDocument } from "@/features/legal/components/LegalDocument";
import { terms } from "@/features/legal/content/terms";

export async function generateMetadata({ params }: PageProps<"/[locale]/terms">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  return { title: terms[locale].title };
}

export default async function Page({ params }: PageProps<"/[locale]/terms">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  return <LegalDocument content={terms[locale]} />;
}

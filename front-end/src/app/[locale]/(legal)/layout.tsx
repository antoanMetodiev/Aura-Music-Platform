import { setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";

/** Public legal pages (privacy, terms): no app shell, no auth gate, plain readable column. */
export default async function LegalLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  return <div className="min-h-dvh bg-background">{children}</div>;
}

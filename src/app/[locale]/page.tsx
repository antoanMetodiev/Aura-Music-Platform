import { redirect } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import type { Locale } from "@/i18n/routing";

/** `/{locale}` has no content of its own — Home is open to everyone, signed in or not. */
export default async function LocaleRootPage({ params }: PageProps<"/[locale]">) {
  const { locale } = (await params) as { locale: Locale };
  redirect({ href: routes.home, locale });
}

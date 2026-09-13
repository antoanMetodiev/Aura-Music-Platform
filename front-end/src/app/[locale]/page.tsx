import { redirect } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import type { Locale } from "@/i18n/routing";

/** `/{locale}` has no content of its own — send everyone to Home (auth gate comes with the Auth slice). */
export default async function LocaleRootPage({ params }: PageProps<"/[locale]">) {
  const { locale } = (await params) as { locale: Locale };
  redirect({ href: routes.home, locale });
}

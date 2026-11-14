import { getRequestConfig } from "next-intl/server";
import { hasLocale } from "next-intl";
import { routing } from "./routing";

export default getRequestConfig(async ({ requestLocale }) => {
  const requested = await requestLocale;
  const locale = hasLocale(routing.locales, requested) ? requested : routing.defaultLocale;

  return {
    locale,
    messages: (await import(`../../messages/${locale}.json`)).default,
    // One "now" per request, inherited by NextIntlClientProvider — so relative times ("7м") render
    // identically on the server and during hydration instead of drifting by the render gap.
    now: new Date(),
  };
});

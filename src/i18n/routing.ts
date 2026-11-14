import { defineRouting } from "next-intl/routing";

export const locales = ["en", "bg"] as const;
export type Locale = (typeof locales)[number];

export const routing = defineRouting({
  locales,
  defaultLocale: "en",
  // Every URL carries its locale: /en/home, /bg/home. No bare /home.
  localePrefix: "always",
});

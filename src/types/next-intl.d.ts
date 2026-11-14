import type messages from "../../messages/en.json";
import type { routing } from "@/i18n/routing";

/** Type-safe `t("nav.home")` keys and locale union across the app. */
declare module "next-intl" {
  interface AppConfig {
    Locale: (typeof routing.locales)[number];
    Messages: typeof messages;
  }
}

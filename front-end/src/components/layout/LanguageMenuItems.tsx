"use client";

import { useTransition } from "react";
import { Languages } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { usePathname, useRouter } from "@/i18n/navigation";
import { type Locale, locales } from "@/i18n/routing";
import {
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from "@/components/ui/dropdown-menu";

/**
 * Language radio group for the account dropdown. Switching keeps the current
 * route and swaps only the locale segment (/en/home ↔ /bg/home).
 */
export function LanguageMenuItems() {
  const t = useTranslations("nav");
  const names = useTranslations("languages");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const [pending, startTransition] = useTransition();

  const change = (next: string) => {
    if (next === locale) return;
    startTransition(() => {
      router.replace(pathname, { locale: next as Locale });
    });
  };

  return (
    <DropdownMenuRadioGroup value={locale} onValueChange={change}>
      <DropdownMenuLabel className="flex items-center gap-1.5">
        <Languages className="size-3.5" /> {t("language")}
      </DropdownMenuLabel>
      {locales.map((code) => (
        <DropdownMenuRadioItem key={code} value={code} disabled={pending}>
          {names(code)}
        </DropdownMenuRadioItem>
      ))}
    </DropdownMenuRadioGroup>
  );
}

import { getTranslations, setRequestLocale } from "next-intl/server";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import type { Locale } from "@/i18n/routing";

/**
 * Sign-in / sign-up / password pages: no app shell, one centered card on the app gradient.
 * Login and register send a signed-in visitor to Home themselves; reset-password must not — the
 * link from the reset email arrives with a (recovery) session already open.
 */
export default async function AuthLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);

  const t = await getTranslations({ locale, namespace: "auth" });

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 bg-background px-4 py-10">
      {children}
      <nav className="flex gap-5 text-xs text-subtle-foreground">
        <Link href={routes.privacy} className="hover:text-foreground">
          {t("legal.privacy")}
        </Link>
        <Link href={routes.terms} className="hover:text-foreground">
          {t("legal.terms")}
        </Link>
      </nav>
    </div>
  );
}

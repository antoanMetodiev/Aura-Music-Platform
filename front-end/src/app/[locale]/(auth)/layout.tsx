import { getTranslations, setRequestLocale } from "next-intl/server";
import { Link, redirect } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import type { Locale } from "@/i18n/routing";
import { getSession } from "@/lib/auth/session";

/**
 * Sign-in / sign-up / password pages: no app shell, one centered card on the app gradient.
 * Someone already signed in has no business here and goes to Home.
 */
export default async function AuthLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);

  if (await getSession()) redirect({ href: routes.home, locale });
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

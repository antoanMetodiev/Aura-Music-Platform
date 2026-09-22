import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Link, redirect } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import type { Locale } from "@/i18n/routing";
import { getSession } from "@/lib/auth/session";
import { AuthCard } from "@/features/auth/components/AuthCard";
import { LoginForm } from "@/features/auth/components/LoginForm";

export async function generateMetadata({ params }: PageProps<"/[locale]/login">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("login") };
}

export default async function Page({ params }: PageProps<"/[locale]/login">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  if (await getSession()) redirect({ href: routes.home, locale });
  const t = await getTranslations({ locale, namespace: "auth" });

  return (
    <AuthCard
      title={t("login.title")}
      subtitle={t("login.subtitle")}
      footer={
        <>
          {t("login.noAccount")}{" "}
          <Link href={routes.register} className="font-medium text-foreground hover:underline">
            {t("login.register")}
          </Link>
        </>
      }
    >
      <LoginForm />
    </AuthCard>
  );
}

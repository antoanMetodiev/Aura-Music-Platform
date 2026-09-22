import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Link, redirect } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import type { Locale } from "@/i18n/routing";
import { getSession } from "@/lib/auth/session";
import { AuthCard } from "@/features/auth/components/AuthCard";
import { RegisterForm } from "@/features/auth/components/RegisterForm";

export async function generateMetadata({ params }: PageProps<"/[locale]/register">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("register") };
}

export default async function Page({ params }: PageProps<"/[locale]/register">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  if (await getSession()) redirect({ href: routes.home, locale });
  const t = await getTranslations({ locale, namespace: "auth" });

  return (
    <AuthCard
      title={t("register.title")}
      subtitle={t("register.subtitle")}
      footer={
        <>
          {t("register.hasAccount")}{" "}
          <Link href={routes.login} className="font-medium text-foreground hover:underline">
            {t("register.login")}
          </Link>
        </>
      }
    >
      <RegisterForm />
    </AuthCard>
  );
}

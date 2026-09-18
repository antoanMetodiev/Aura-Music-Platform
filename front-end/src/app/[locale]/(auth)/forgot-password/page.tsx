import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import type { Locale } from "@/i18n/routing";
import { AuthCard } from "@/features/auth/components/AuthCard";
import { ForgotPasswordForm } from "@/features/auth/components/ForgotPasswordForm";

export async function generateMetadata({ params }: PageProps<"/[locale]/forgot-password">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("forgotPassword") };
}

export default async function Page({ params }: PageProps<"/[locale]/forgot-password">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations({ locale, namespace: "auth" });

  return (
    <AuthCard
      title={t("forgot.title")}
      subtitle={t("forgot.subtitle")}
      footer={
        <Link href={routes.login} className="font-medium text-foreground hover:underline">
          {t("forgot.back")}
        </Link>
      }
    >
      <ForgotPasswordForm />
    </AuthCard>
  );
}

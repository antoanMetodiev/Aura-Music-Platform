import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import type { Locale } from "@/i18n/routing";
import { AuthCard } from "@/features/auth/components/AuthCard";
import { ResetPasswordForm } from "@/features/auth/components/ResetPasswordForm";

export async function generateMetadata({ params }: PageProps<"/[locale]/reset-password">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("resetPassword") };
}

export default async function Page({ params }: PageProps<"/[locale]/reset-password">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations({ locale, namespace: "auth" });

  return (
    <AuthCard
      title={t("reset.title")}
      subtitle={t("reset.subtitle")}
      footer={
        <Link href={routes.login} className="font-medium text-foreground hover:underline">
          {t("forgot.back")}
        </Link>
      }
    >
      <ResetPasswordForm />
    </AuthCard>
  );
}

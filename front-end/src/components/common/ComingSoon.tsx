import { Construction } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";

type PageKey = Parameters<ReturnType<typeof useTranslations<"pages">>>[0];

/** Placeholder for routes that exist in the nav but aren't built yet. */
export function ComingSoon({ titleKey }: { titleKey: PageKey }) {
  const t = useTranslations("common");
  const pages = useTranslations("pages");

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-6 text-center">
      <span className="grid size-14 place-items-center rounded-full border border-border bg-elevated">
        <Construction className="size-6 text-muted-foreground" strokeWidth={1.5} />
      </span>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{pages(titleKey)}</h1>
        <p className="mt-1 max-w-sm text-sm text-muted-foreground">{t("comingSoon")}</p>
      </div>
      <Link href={routes.home} className="text-sm font-medium text-primary-hover hover:underline">
        {t("backToHome")}
      </Link>
    </div>
  );
}

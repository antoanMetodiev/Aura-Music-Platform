import Image from "next/image";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";

/** App icon (public/Aura-logo.png) + typographic wordmark. */
export function AuraLogo({ compact = false, className }: { compact?: boolean; className?: string }) {
  const t = useTranslations("app");

  return (
    <Link
      href={routes.home}
      aria-label={t("name")}
      className={cn("flex items-center gap-2.5 outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md", className)}
    >
      {/* The PNG already carries its own rounded-square shape. `unoptimized`: a 38 KB static file
          needs no resizing pass through the Workers Images binding. */}
      <Image src="/Aura-logo.png" alt="" width={28} height={28} unoptimized priority className="size-7 shrink-0" />
      {!compact && (
        <span className="text-[15px] font-semibold tracking-[0.18em] uppercase text-foreground">
          {t("name")}
        </span>
      )}
    </Link>
  );
}

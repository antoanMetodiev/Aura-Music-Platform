import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";

/** Typographic wordmark + gradient mark. Swap for the real SVG when we have one. */
export function AuraLogo({ compact = false, className }: { compact?: boolean; className?: string }) {
  const t = useTranslations("app");

  return (
    <Link
      href={routes.home}
      aria-label={t("name")}
      className={cn("flex items-center gap-2.5 outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md", className)}
    >
      <span className="relative grid size-7 shrink-0 place-items-center overflow-hidden rounded-md bg-gradient-to-br from-[#4c82ff] via-[#2f6bff] to-[#0b2a5b] shadow-[0_0_0_1px_rgba(255,255,255,0.06)_inset]">
        <span className="absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(255,255,255,0.35),transparent_55%)]" />
        <svg viewBox="0 0 24 24" className="relative size-4 text-white" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
          <path d="M6 15c1.5-4 3-6 6-6s4.5 2 6 6" />
          <path d="M9 17.5c.8-2 1.8-3 3-3s2.2 1 3 3" />
        </svg>
      </span>
      {!compact && (
        <span className="text-[15px] font-semibold tracking-[0.18em] uppercase text-foreground">
          {t("name")}
        </span>
      )}
    </Link>
  );
}

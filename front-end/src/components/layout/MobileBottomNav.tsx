"use client";

import { Home, Library, Search, User, Users } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link, usePathname } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";

export function MobileBottomNav({ profileHref }: { profileHref: string }) {
  const t = useTranslations("nav");
  const pathname = usePathname();

  const items = [
    { label: t("home"), href: routes.home, icon: Home },
    { label: t("search"), href: routes.search(), icon: Search },
    { label: t("library"), href: routes.library, icon: Library },
    { label: t("friends"), href: routes.friends, icon: Users },
    { label: t("profile"), href: profileHref, icon: User },
  ];

  return (
    <nav
      aria-label={t("mobile")}
      className="flex h-16 items-stretch border-t border-border bg-background pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      {items.map(({ label, href, icon: Icon }) => {
        const active = href === routes.home ? pathname === href : pathname.startsWith(href);
        return (
          <Link
            key={href}
            href={href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "flex flex-1 flex-col items-center justify-center gap-1 text-[10px] font-medium transition-colors",
              active ? "text-foreground" : "text-muted-foreground",
            )}
          >
            <Icon className={cn("size-5", active && "text-primary-hover")} strokeWidth={active ? 2.2 : 1.8} />
            {label}
          </Link>
        );
      })}
    </nav>
  );
}

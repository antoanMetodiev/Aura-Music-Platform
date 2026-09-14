"use client";

import {
  Bell,
  Clock,
  Heart,
  Home,
  Library,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Search,
  Users,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { Link, usePathname } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";
import { useUiStore } from "@/lib/store/ui-store";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import type { PlaylistSummary } from "@/types/catalog";
import { AuraLogo } from "./AuraLogo";
import { ResizeHandle } from "./ResizeHandle";

type NavKey = "home" | "search" | "library" | "likedSongs" | "recentlyPlayed" | "friends" | "notifications";

const primaryNav: { key: NavKey; href: string; icon: React.ComponentType<{ className?: string }>; badge?: number }[] = [
  { key: "home", href: routes.home, icon: Home },
  { key: "search", href: routes.search(), icon: Search },
  { key: "library", href: routes.library, icon: Library },
];

const secondaryNav: typeof primaryNav = [
  { key: "likedSongs", href: routes.liked, icon: Heart },
  { key: "recentlyPlayed", href: routes.recentlyPlayed, icon: Clock },
  { key: "friends", href: routes.friends, icon: Users, badge: 2 },
  { key: "notifications", href: routes.notifications, icon: Bell, badge: 5 },
];

interface SidebarProps {
  playlists: PlaylistSummary[];
}

/**
 * Desktop/tablet navigation.
 *   md–lg : icon rail (labels hidden via CSS, no JS state involved)
 *   ≥ lg  : full width, user-collapsible to the same icon rail
 * Below md the MobileBottomNav takes over.
 */
export function Sidebar({ playlists }: SidebarProps) {
  const t = useTranslations("nav");
  const pathname = usePathname();
  const collapsed = useUiStore((s) => s.sidebarCollapsed);
  const width = useUiStore((s) => s.sidebarWidth);
  const resizing = useUiStore((s) => s.resizing);
  const toggleSidebar = useUiStore((s) => s.toggleSidebar);

  const isActive = (href: string) =>
    href === routes.home ? pathname === href : pathname.startsWith(href);

  // Label visibility: hidden when collapsed; otherwise hidden below lg by CSS.
  const label = collapsed ? "hidden" : "hidden lg:block";
  const rowLayout = collapsed ? "justify-center px-0" : "max-lg:justify-center max-lg:px-0";

  return (
    <>
    <aside
      style={{ "--sidebar-w": `${width}px` } as React.CSSProperties}
      className={cn(
        "hidden shrink-0 flex-col rounded-xl bg-panel md:flex",
        resizing ? "transition-none" : "transition-[width] duration-200",
        collapsed ? "w-[68px]" : "w-[68px] lg:w-(--sidebar-w)",
      )}
    >
      {/* Brand + collapse */}
      <div className={cn("flex h-16 items-center px-4", collapsed ? "justify-center" : "max-lg:justify-center lg:justify-between")}>
        <AuraLogo compact className={collapsed ? "" : "lg:hidden"} />
        {!collapsed && <AuraLogo className="max-lg:hidden" />}
        {!collapsed && (
          <Tooltip>
            <TooltipTrigger
              render={
                <Button variant="ghost" size="icon-sm" onClick={toggleSidebar} aria-label={t("collapseSidebar")} className="max-lg:hidden" />
              }
            >
              <PanelLeftClose className="text-muted-foreground" />
            </TooltipTrigger>
            <TooltipContent side="right">{t("collapse")}</TooltipContent>
          </Tooltip>
        )}
      </div>

      <nav className="flex flex-col gap-0.5 px-2" aria-label={t("primary")}>
        {primaryNav.map((item) => (
          <NavItem key={item.href} href={item.href} icon={item.icon} badge={item.badge} label={t(item.key)} active={isActive(item.href)} collapsed={collapsed} labelClass={label} rowClass={rowLayout} />
        ))}
      </nav>

      <div className="mx-4 my-3 h-px bg-border" />

      <nav className="flex flex-col gap-0.5 px-2" aria-label={t("personal")}>
        {secondaryNav.map((item) => (
          <NavItem key={item.href} href={item.href} icon={item.icon} badge={item.badge} label={t(item.key)} active={isActive(item.href)} collapsed={collapsed} labelClass={label} rowClass={rowLayout} />
        ))}
      </nav>

      <div className="mx-4 my-3 h-px bg-border" />

      {/* Playlists */}
      <div className={cn("flex items-center px-4 pb-2", collapsed ? "justify-center" : "max-lg:justify-center lg:justify-between")}>
        <span className={cn("text-[11px] font-semibold tracking-[0.14em] uppercase text-subtle-foreground", label)}>
          {t("playlists")}
        </span>
        <Tooltip>
          <TooltipTrigger render={<Button variant="ghost" size="icon-sm" aria-label={t("createPlaylist")} />}>
            <Plus className="text-muted-foreground" />
          </TooltipTrigger>
          <TooltipContent side="right">{t("createPlaylist")}</TooltipContent>
        </Tooltip>
      </div>

      <ul className="scrollbar-thin min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        {playlists.map((playlist) => {
          const href = routes.playlist(playlist.id);
          const active = pathname === href;
          const row = (
            <Link
              href={href}
              className={cn(
                "group flex items-center gap-3 rounded-md p-2 text-sm transition-colors hover:bg-hover",
                active && "bg-active",
                rowLayout,
              )}
            >
              <ArtworkImage
                artwork={playlist.artwork}
                alt={playlist.title}
                seed={playlist.id}
                sizes="40px"
                className="size-10 shrink-0"
                iconClassName="size-4"
              />
              <span className={cn("min-w-0", label)}>
                <span className={cn("block truncate font-medium", active ? "text-foreground" : "text-foreground/90")}>
                  {playlist.title}
                </span>
                <span className="block truncate text-xs text-muted-foreground">
                  {t("playlistMeta", { owner: playlist.owner.displayName, count: playlist.trackCount })}
                </span>
              </span>
            </Link>
          );
          return (
            <li key={playlist.id}>
              <Tooltip>
                <TooltipTrigger render={row} />
                <TooltipContent side="right" className={cn(!collapsed && "lg:hidden")}>
                  {playlist.title}
                </TooltipContent>
              </Tooltip>
            </li>
          );
        })}
      </ul>

      {collapsed && (
        <div className="flex justify-center border-t border-border p-2 max-lg:hidden">
          <Tooltip>
            <TooltipTrigger
              render={<Button variant="ghost" size="icon-sm" onClick={toggleSidebar} aria-label={t("expandSidebar")} />}
            >
              <PanelLeftOpen className="text-muted-foreground" />
            </TooltipTrigger>
            <TooltipContent side="right">{t("expand")}</TooltipContent>
          </Tooltip>
        </div>
      )}
    </aside>
    {/* Below lg the sidebar is a fixed icon rail — the gutter stays as spacing but can't be dragged. */}
    <ResizeHandle panel="sidebar" className="hidden md:block max-lg:pointer-events-none" />
    </>
  );
}

interface NavItemProps {
  label: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  active: boolean;
  collapsed: boolean;
  labelClass: string;
  rowClass: string;
  badge?: number;
}

function NavItem({ label, href, icon: Icon, active, collapsed, labelClass, rowClass, badge }: NavItemProps) {
  const link = (
    <Link
      href={href}
      aria-current={active ? "page" : undefined}
      className={cn(
        "relative flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors",
        "text-muted-foreground hover:bg-hover hover:text-foreground",
        active && "bg-active text-foreground",
        rowClass,
      )}
    >
      {active && !collapsed && (
        <span className="absolute top-1/2 left-0 h-5 w-0.5 -translate-y-1/2 rounded-r bg-primary max-lg:hidden" />
      )}
      <Icon className={cn("size-[18px] shrink-0", active && "text-primary-hover")} />
      <span className={cn("flex-1 truncate", labelClass)}>{label}</span>
      {badge ? (
        <span
          className={cn(
            "grid min-w-5 place-items-center rounded-full bg-primary px-1 font-mono text-[10px] leading-5 font-semibold text-primary-foreground",
            collapsed
              ? "absolute top-1 right-1 min-w-4 leading-4"
              : "max-lg:absolute max-lg:top-1 max-lg:right-1 max-lg:min-w-4 max-lg:leading-4",
          )}
        >
          {badge}
        </span>
      ) : null}
    </Link>
  );

  return (
    <Tooltip>
      <TooltipTrigger render={link} />
      <TooltipContent side="right" className={cn(!collapsed && "lg:hidden")}>
        {label}
      </TooltipContent>
    </Tooltip>
  );
}

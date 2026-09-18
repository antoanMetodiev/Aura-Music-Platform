"use client";

import {
  Bell,
  ChevronLeft,
  ChevronRight,
  Languages,
  LogOut,
  Settings,
  User,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { Link, useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { cn } from "@/lib/utils";
import { signOut } from "@/lib/auth/client";
import { clearClientAccessToken } from "@/lib/api/token";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { UserAvatar } from "@/components/common/UserAvatar";
import { GlobalSearchInput } from "@/features/search/components/GlobalSearchInput";
import type { UserSummary } from "@/types/social";
import { AuraLogo } from "./AuraLogo";
import { LanguageMenuItems } from "./LanguageMenuItems";

interface TopBarProps {
  user: UserSummary | null;
  unreadNotifications?: number;
}

/**
 * Sticky bar at the top of the main scroll area. Transparent so hero gradients
 * show through, with a blur once content scrolls under it.
 */
export function TopBar({ user, unreadNotifications = 0 }: TopBarProps) {
  const t = useTranslations("nav");
  const router = useRouter();

  return (
    <header
      className={cn(
        "sticky top-0 z-30 flex h-16 items-center gap-3 px-4 sm:px-6",
        "bg-panel/70 backdrop-blur-md supports-[backdrop-filter]:bg-panel/55",
      )}
    >
      {/* Mobile brand */}
      <AuraLogo className="md:hidden" />

      {/* History nav (desktop) */}
      <div className="hidden items-center gap-1 lg:flex">
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("goBack")}
          onClick={() => router.back()}
        >
          <ChevronLeft className="size-5 text-muted-foreground" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("goForward")}
          onClick={() => router.forward()}
        >
          <ChevronRight className="size-5 text-muted-foreground" />
        </Button>
      </div>

      <GlobalSearchInput className="mx-auto hidden w-full max-w-md md:block" />

      <div className="ml-auto flex items-center gap-1.5">
        {user ? (
          <SignedInControls
            user={user}
            unreadNotifications={unreadNotifications}
          />
        ) : (
          <GuestControls />
        )}
      </div>
    </header>
  );
}

/** Guest: language switcher + sign-in / sign-up. Same spot the avatar menu takes once signed in. */
function GuestControls() {
  const t = useTranslations("nav");
  const auth = useTranslations("auth");

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          aria-label={t("language")}
          className="grid size-9 place-items-center rounded-full text-muted-foreground outline-none transition-colors hover:bg-hover hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
        >
          <Languages className="size-[18px]" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-44">
          <LanguageMenuItems />
        </DropdownMenuContent>
      </DropdownMenu>
      <Button
        variant="ghost"
        className="max-sm:hidden"
        render={<Link href={routes.register} />}
      >
        {auth("register.submit")}
      </Button>
      <Button render={<Link href={routes.login} />}>
        {auth("login.submit")}
      </Button>
    </>
  );
}

function SignedInControls({
  user,
  unreadNotifications,
}: {
  user: UserSummary;
  unreadNotifications: number;
}) {
  const t = useTranslations("nav");
  const router = useRouter();

  return (
    <>
      <Tooltip>
        <TooltipTrigger
          render={
            <Link
              href={routes.notifications}
              aria-label={
                unreadNotifications
                  ? t("notificationsUnread", { count: unreadNotifications })
                  : t("notifications")
              }
              className="relative grid size-9 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-hover hover:text-foreground"
            />
          }
        >
          <Bell className="size-[18px]" />
          {unreadNotifications > 0 && (
            <span className="absolute top-1.5 right-1.5 size-2 rounded-full bg-primary ring-2 ring-panel" />
          )}
        </TooltipTrigger>
        <TooltipContent>{t("notifications")}</TooltipContent>
      </Tooltip>

      <DropdownMenu>
        <DropdownMenuTrigger
          aria-label={t("accountMenu")}
          className="rounded-full outline-none ring-offset-panel transition-shadow hover:ring-2 hover:ring-border-strong focus-visible:ring-2 focus-visible:ring-ring"
        >
          <UserAvatar user={user} />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-56">
          <DropdownMenuGroup>
            <DropdownMenuLabel className="flex flex-col">
              <span className="font-medium text-foreground">
                {user.displayName}
              </span>
              <span className="text-xs font-normal text-muted-foreground">
                @{user.username}
              </span>
            </DropdownMenuLabel>
            <DropdownMenuItem
              render={<Link href={routes.profile(user.username)} />}
            >
              <User /> {t("profile")}
            </DropdownMenuItem>
            <DropdownMenuItem render={<Link href={routes.settings} />}>
              <Settings /> {t("settings")}
            </DropdownMenuItem>
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          <LanguageMenuItems />
          <DropdownMenuSeparator />
          <DropdownMenuItem
            variant="destructive"
            onClick={async () => {
              await signOut();
              clearClientAccessToken();
              router.replace(routes.home);
              router.refresh();
            }}
          >
            <LogOut /> {t("logOut")}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </>
  );
}

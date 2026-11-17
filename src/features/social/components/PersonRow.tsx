import type { ReactNode } from "react";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { UserAvatar } from "@/components/common/UserAvatar";
import type { UserSummary } from "@/types/social";

interface PersonRowProps {
  user: UserSummary;
  /** Secondary line under the @username, e.g. "Friends since March 2026". */
  meta?: ReactNode;
  /** Buttons on the right. */
  actions?: ReactNode;
}

/** One person in a list: avatar + name + @username (linking to their profile), actions on the right. */
export function PersonRow({ user, meta, actions }: PersonRowProps) {
  return (
    <li className="flex items-center gap-3 rounded-lg px-2 py-2 transition-colors hover:bg-white/[0.04]">
      <Link href={routes.profile(user.username)} className="flex min-w-0 flex-1 items-center gap-3">
        <UserAvatar user={user} className="size-11" />
        <span className="min-w-0">
          <span className="block truncate font-medium">{user.displayName}</span>
          <span className="block truncate text-sm text-muted-foreground">
            @{user.username}
            {meta ? <> · {meta}</> : null}
          </span>
        </span>
      </Link>
      {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
    </li>
  );
}

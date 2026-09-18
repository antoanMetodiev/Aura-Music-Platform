import { setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/AppShell";
import { toUserSummary } from "@/features/auth/lib/toUserSummary";
import { getSession } from "@/lib/auth/session";
import { userPlaylists } from "@/lib/mock/catalog";
import { activityFeed, friendPresence } from "@/lib/mock/social";

/**
 * The app area. Wraps every page in the app shell — for guests too: the session is optional and
 * the shell shows sign-in / sign-up instead of the account menu when there is none.
 * TEMPORARY: playlists / presence / activity come from mocks until the Library and Social
 * services exist.
 */
export default async function AppLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);

  const session = await getSession();

  return (
    <AppShell
      user={session ? toUserSummary(session.user) : null}
      playlists={userPlaylists}
      presence={friendPresence}
      activity={activityFeed}
      unreadNotifications={session ? 5 : 0}
    >
      {children}
    </AppShell>
  );
}

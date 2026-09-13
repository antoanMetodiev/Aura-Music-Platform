import { setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/AppShell";
import { userPlaylists } from "@/lib/mock/catalog";
import { activityFeed, currentUser, friendPresence } from "@/lib/mock/social";

/**
 * Authenticated area. Wraps every page in the app shell.
 * TEMPORARY: shell data comes from mocks until Supabase auth + the Social /
 * Library services are wired (then: session user, own playlists, realtime presence).
 */
export default async function AppLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);

  return (
    <AppShell
      user={currentUser}
      playlists={userPlaylists}
      presence={friendPresence}
      activity={activityFeed}
      unreadNotifications={5}
    >
      {children}
    </AppShell>
  );
}

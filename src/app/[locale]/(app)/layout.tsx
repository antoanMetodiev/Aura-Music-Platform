import { setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/AppShell";
import { getAccessToken, getSession, toUserSummary } from "@/lib/auth/session";
import { getMyPlaylists, type LibraryPlaylist } from "@/features/library/api/libraryApi";

/**
 * The app area. Wraps every page in the app shell — for guests too: the session is optional and
 * the shell shows sign-in / sign-up instead of the account menu when there is none.
 * A signed-in user's playlists come from library-svc; friend presence, activity and notifications
 * stay empty until socials-svc exists — the shell shows empty states rather than invented data.
 */
export default async function AppLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);

  const session = await getSession();
  let playlists: LibraryPlaylist[] | null = null;
  if (session) {
    try {
      playlists = await getMyPlaylists(await getAccessToken(), session.user.name);
    } catch {
      // library-svc asleep or down — the sidebar shows its empty state and the client retries.
    }
  }

  return (
    <AppShell
      user={session ? toUserSummary(session.user) : null}
      playlists={playlists}
      presence={[]}
      activity={[]}
      unreadNotifications={0}
    >
      {children}
    </AppShell>
  );
}

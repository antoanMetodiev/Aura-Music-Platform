import { setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { AppShell } from "@/components/layout/AppShell";
import { getSession, toUserSummary } from "@/lib/auth/session";

/**
 * The app area. Wraps every page in the app shell — for guests too: the session is optional and
 * the shell shows sign-in / sign-up instead of the account menu when there is none.
 * Playlists, friend presence, activity and notifications stay empty until library-svc and
 * socials-svc exist — the shell shows empty states rather than invented data.
 */
export default async function AppLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);

  const session = await getSession();

  return (
    <AppShell
      user={session ? toUserSummary(session.user) : null}
      playlists={[]}
      presence={[]}
      activity={[]}
      unreadNotifications={0}
    >
      {children}
    </AppShell>
  );
}

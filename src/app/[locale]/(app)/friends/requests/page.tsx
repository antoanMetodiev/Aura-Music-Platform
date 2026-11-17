import type { Metadata } from "next";
import { ChevronLeft, Inbox, Send, UserRoundX } from "lucide-react";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { getAccessToken, getSession } from "@/lib/auth/session";
import { EmptyState } from "@/components/common/EmptyState";
import { getIncomingRequests, getOutgoingRequests, type Connection } from "@/features/social/api/socialApi";
import { FriendButton } from "@/features/social/components/FriendButton";
import { PersonRow } from "@/features/social/components/PersonRow";
import { RelativeTime } from "@/features/social/components/RelativeTime";

export async function generateMetadata({ params }: PageProps<"/[locale]/friends/requests">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("friendRequests") };
}

/** Requests people sent you (accept / decline) and the ones you sent (cancel). */
export default async function FriendRequestsPage({ params }: PageProps<"/[locale]/friends/requests">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations("social");
  const pages = await getTranslations("pages");
  const session = await getSession();

  if (!session) {
    return (
      <EmptyState
        icon={Inbox}
        title={pages("friendRequests")}
        description={t("signInForFriends")}
        action={
          <Link href={routes.login} className="text-sm font-medium text-primary-hover hover:underline">
            {t("signIn")}
          </Link>
        }
        className="min-h-[60vh]"
      />
    );
  }

  const token = await getAccessToken();
  let incoming: Connection[] | null = null;
  let outgoing: Connection[] | null = null;
  try {
    [incoming, outgoing] = await Promise.all([getIncomingRequests(token), getOutgoingRequests(token)]);
  } catch {
    // shown below
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8 px-3 pt-6 pb-12 sm:px-5 sm:pt-10">
      <header>
        <Link href={routes.friends} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ChevronLeft className="size-4" />
          {pages("friends")}
        </Link>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">{pages("friendRequests")}</h1>
      </header>

      {incoming === null || outgoing === null ? (
        <EmptyState icon={UserRoundX} title={t("unavailable")} className="min-h-[30vh]" />
      ) : (
        <>
          <section className="flex flex-col gap-2">
            <h2 className="px-2 text-sm font-semibold tracking-[0.14em] text-subtle-foreground uppercase">
              {t("received", { count: incoming.length })}
            </h2>
            {incoming.length === 0 ? (
              <EmptyState icon={Inbox} title={t("noIncoming")} className="min-h-[16vh]" />
            ) : (
              <ul className="flex flex-col">
                {incoming.map(({ user, since }) => (
                  <PersonRow
                    key={user.id}
                    user={user}
                    meta={<RelativeTime iso={since} />}
                    actions={<FriendButton userId={user.id} displayName={user.displayName} initial="REQUEST_RECEIVED" compact />}
                  />
                ))}
              </ul>
            )}
          </section>

          <section className="flex flex-col gap-2">
            <h2 className="px-2 text-sm font-semibold tracking-[0.14em] text-subtle-foreground uppercase">
              {t("sent", { count: outgoing.length })}
            </h2>
            {outgoing.length === 0 ? (
              <EmptyState icon={Send} title={t("noOutgoing")} className="min-h-[16vh]" />
            ) : (
              <ul className="flex flex-col">
                {outgoing.map(({ user, since }) => (
                  <PersonRow
                    key={user.id}
                    user={user}
                    meta={<RelativeTime iso={since} />}
                    actions={<FriendButton userId={user.id} displayName={user.displayName} initial="REQUEST_SENT" compact />}
                  />
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  );
}

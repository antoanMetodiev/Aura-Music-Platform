import type { Metadata } from "next";
import { ChevronRight, UserRoundX, Users } from "lucide-react";
import { getFormatter, getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { getAccessToken, getSession } from "@/lib/auth/session";
import { EmptyState } from "@/components/common/EmptyState";
import { getFriends, getSocialSummary, type Connection } from "@/features/social/api/socialApi";
import { FriendButton } from "@/features/social/components/FriendButton";
import { PeopleSearch } from "@/features/social/components/PeopleSearch";
import { PersonRow } from "@/features/social/components/PersonRow";

export async function generateMetadata({ params }: PageProps<"/[locale]/friends">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("friends") };
}

/** Your friends, a way to find new ones, and a link to pending requests (socials-svc). */
export default async function FriendsPage({ params }: PageProps<"/[locale]/friends">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations("social");
  const pages = await getTranslations("pages");
  const format = await getFormatter();
  const session = await getSession();

  if (!session) {
    return (
      <EmptyState
        icon={Users}
        title={pages("friends")}
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
  let friends: Connection[] | null = null;
  let incoming = 0;
  try {
    const [list, summary] = await Promise.all([getFriends(token), getSocialSummary(token)]);
    friends = list;
    incoming = summary.incomingRequests;
  } catch {
    // shown below
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8 px-3 pt-6 pb-12 sm:px-5 sm:pt-10">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{pages("friends")}</h1>
          {friends ? <p className="mt-1 text-sm text-muted-foreground">{t("friendCount", { count: friends.length })}</p> : null}
        </div>
        <Link
          href={routes.friendRequests}
          className="inline-flex h-9 items-center gap-2 rounded-full border border-border-strong px-4 text-sm font-medium hover:border-foreground"
        >
          {t("requests")}
          {incoming > 0 ? (
            <span className="grid min-w-5 place-items-center rounded-full bg-primary px-1.5 text-xs font-semibold text-primary-foreground">
              {incoming}
            </span>
          ) : null}
          <ChevronRight className="size-4 opacity-70" />
        </Link>
      </header>

      <PeopleSearch />

      <section className="flex flex-col gap-2">
        <h2 className="px-2 text-sm font-semibold tracking-[0.14em] text-subtle-foreground uppercase">{t("yourFriends")}</h2>
        {friends === null ? (
          <EmptyState icon={UserRoundX} title={t("unavailable")} className="min-h-[24vh]" />
        ) : friends.length === 0 ? (
          <EmptyState icon={Users} title={t("noFriendsYet")} description={t("noFriendsYetHint")} className="min-h-[24vh]" />
        ) : (
          <ul className="flex flex-col">
            {friends.map(({ user, since }) => (
              <PersonRow
                key={user.id}
                user={user}
                meta={t("friendsSince", { date: format.dateTime(new Date(since), { month: "long", year: "numeric" }) })}
                actions={<FriendButton userId={user.id} displayName={user.displayName} initial="FRIENDS" compact />}
              />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

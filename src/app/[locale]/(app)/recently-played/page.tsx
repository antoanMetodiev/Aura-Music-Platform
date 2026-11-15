import type { Metadata } from "next";
import { Clock, CloudOff } from "lucide-react";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { getAccessToken, getSession } from "@/lib/auth/session";
import { EmptyState } from "@/components/common/EmptyState";
import { getRecentlyPlayed, type RecentlyPlayed } from "@/features/activity/api/activityApi";
import { PlayButton } from "@/features/music/components/PlayButton";
import { TrackRow } from "@/features/music/components/TrackRow";

export async function generateMetadata({ params }: PageProps<"/[locale]/recently-played">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("recentlyPlayed") };
}

/** Distinct tracks by their latest real listen (30 s or more), newest first. */
export default async function RecentlyPlayedPage({ params }: PageProps<"/[locale]/recently-played">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations("activity");
  const pages = await getTranslations("pages");
  const session = await getSession();

  if (!session) {
    return (
      <EmptyState
        icon={Clock}
        title={pages("recentlyPlayed")}
        description={t("signInForHistory")}
        action={
          <Link href={routes.login} className="text-sm font-medium text-primary-hover hover:underline">
            {t("signIn")}
          </Link>
        }
        className="min-h-[60vh]"
      />
    );
  }

  let plays: RecentlyPlayed[] | null = null;
  try {
    plays = await getRecentlyPlayed(await getAccessToken(), 100);
  } catch {
    // shown below
  }
  const tracks = plays?.map((p) => p.track) ?? [];

  return (
    <div className="px-3 py-6 sm:px-5">
      <div className="mb-4 flex items-center gap-4 px-3">
        {tracks.length > 0 && <PlayButton tracks={tracks} contextId="recently-played" size="lg" />}
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{pages("recentlyPlayed")}</h1>
      </div>
      {plays === null ? (
        <EmptyState icon={CloudOff} title={t("unavailable")} className="min-h-[40vh]" />
      ) : tracks.length === 0 ? (
        <EmptyState icon={Clock} title={t("nothingYet")} description={t("nothingYetHint")} className="min-h-[40vh]" />
      ) : (
        <ol className="flex flex-col">
          {tracks.map((track, index) => (
            <TrackRow key={track.id} index={index} track={track} context={tracks} />
          ))}
        </ol>
      )}
    </div>
  );
}

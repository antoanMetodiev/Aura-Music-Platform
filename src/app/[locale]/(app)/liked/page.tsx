import type { Metadata } from "next";
import { Heart, HeartCrack } from "lucide-react";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { getAccessToken, getSession } from "@/lib/auth/session";
import { EmptyState } from "@/components/common/EmptyState";
import { getLikedTracks, type LikedTracksPage } from "@/features/library/api/libraryApi";
import { CollectionHero, LikedCover } from "@/features/library/components/CollectionHero";
import { LikedTrackList } from "@/features/library/components/LikedTrackList";

export async function generateMetadata({ params }: PageProps<"/[locale]/liked">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("liked") };
}

/** Most recently liked first; the newest 100 for now. */
export default async function LikedPage({ params }: PageProps<"/[locale]/liked">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations("library");
  const pages = await getTranslations("pages");
  const session = await getSession();

  if (!session) {
    return (
      <EmptyState
        icon={Heart}
        title={pages("liked")}
        description={t("signInForLiked")}
        action={
          <Link href={routes.login} className="text-sm font-medium text-primary-hover hover:underline">
            {t("signIn")}
          </Link>
        }
        className="min-h-[60vh]"
      />
    );
  }

  let page: LikedTracksPage | null = null;
  try {
    page = await getLikedTracks(await getAccessToken(), 100);
  } catch {
    // shown below
  }

  return (
    <div className="relative">
      <div aria-hidden className="pointer-events-none absolute inset-x-0 -top-16 h-[480px] bg-gradient-hero opacity-90" />
      <div className="relative flex flex-col gap-8 px-3 pt-6 pb-12 sm:px-5 sm:pt-10">
        <CollectionHero
          cover={
            <LikedCover>
              <Heart className="size-16 fill-white text-white" />
            </LikedCover>
          }
          eyebrow={t("playlist")}
          title={<h1>{pages("liked")}</h1>}
          meta={session.user.name}
        />
        {page === null ? (
          <EmptyState icon={HeartCrack} title={t("unavailable")} className="min-h-[30vh]" />
        ) : page.tracks.length === 0 ? (
          <EmptyState icon={Heart} title={t("noLikedYet")} description={t("noLikedYetHint")} className="min-h-[30vh]" />
        ) : (
          <LikedTrackList tracks={page.tracks.map((item) => item.track)} />
        )}
      </div>
    </div>
  );
}

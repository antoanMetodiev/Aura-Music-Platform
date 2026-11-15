import { cache } from "react";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ListMusic } from "lucide-react";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { ApiError } from "@/lib/api/client";
import { getAccessToken, getSession } from "@/lib/auth/session";
import { EmptyState } from "@/components/common/EmptyState";
import { getPlaylist, type PlaylistDetail } from "@/features/library/api/libraryApi";
import { PlaylistView } from "@/features/library/components/PlaylistView";

// generateMetadata and the page share one backend call per request.
const loadPlaylist = cache(async (id: string): Promise<PlaylistDetail | null> => {
  const token = await getAccessToken();
  if (!token) return null;
  try {
    return await getPlaylist(token, id);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) notFound();
    throw error;
  }
});

export async function generateMetadata({ params }: PageProps<"/[locale]/playlists/[playlistId]">): Promise<Metadata> {
  const { locale, playlistId } = (await params) as { locale: Locale; playlistId: string };
  const detail = await loadPlaylist(playlistId).catch(() => null);
  if (detail) return { title: detail.playlist.title };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("playlist") };
}

export default async function PlaylistPage({ params }: PageProps<"/[locale]/playlists/[playlistId]">) {
  const { locale, playlistId } = (await params) as { locale: Locale; playlistId: string };
  setRequestLocale(locale);
  const t = await getTranslations("library");
  const session = await getSession();

  // The library API needs a signed-in user even for public playlists (for now).
  if (!session) {
    return (
      <EmptyState
        icon={ListMusic}
        title={t("signInForPlaylist")}
        action={
          <Link href={routes.login} className="text-sm font-medium text-primary-hover hover:underline">
            {t("signIn")}
          </Link>
        }
        className="min-h-[60vh]"
      />
    );
  }

  const detail = await loadPlaylist(playlistId);
  if (!detail) notFound();
  const isOwner = detail.playlist.owner.id === session.user.sub;

  return <PlaylistView key={playlistId} detail={detail} isOwner={isOwner} ownerName={isOwner ? session.user.name : ""} />;
}

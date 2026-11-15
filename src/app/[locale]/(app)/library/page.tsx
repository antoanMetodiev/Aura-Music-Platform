import type { Metadata } from "next";
import { Library } from "lucide-react";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { getAccessToken, getSession } from "@/lib/auth/session";
import { EmptyState } from "@/components/common/EmptyState";
import { getMyPlaylists, type LibraryPlaylist } from "@/features/library/api/libraryApi";
import { LibraryGrid } from "@/features/library/components/LibraryGrid";

export async function generateMetadata({ params }: PageProps<"/[locale]/library">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("library") };
}

export default async function LibraryPage({ params }: PageProps<"/[locale]/library">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations("library");
  const pages = await getTranslations("pages");
  const session = await getSession();

  if (!session) {
    return (
      <EmptyState
        icon={Library}
        title={pages("library")}
        description={t("signInForLibrary")}
        action={
          <Link href={routes.login} className="text-sm font-medium text-primary-hover hover:underline">
            {t("signIn")}
          </Link>
        }
        className="min-h-[60vh]"
      />
    );
  }

  let playlists: LibraryPlaylist[] = [];
  try {
    playlists = await getMyPlaylists(await getAccessToken(), session.user.name);
  } catch {
    // The grid still offers Liked songs and "New playlist"; the store refills it when the service answers.
  }

  return (
    <div className="px-3 py-6 sm:px-5">
      <h1 className="mb-4 px-3 text-2xl font-semibold tracking-tight sm:text-3xl">{pages("library")}</h1>
      <LibraryGrid initialPlaylists={playlists} />
    </div>
  );
}

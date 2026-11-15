import { useTranslations } from "next-intl";
import { routes } from "@/config/routes";
import { MediaCard } from "@/features/music/components/MediaCard";
import type { Album, Artist } from "@/types/catalog";

const GRID = "grid grid-cols-2 gap-1 sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-6";

/** The Library page's saved albums and followed artists. Sections with nothing in them are left out. */
export function SavedCollections({ albums, artists }: { albums: Album[]; artists: Artist[] }) {
  const t = useTranslations("library");

  return (
    <>
      {albums.length > 0 && (
        <section className="mt-8">
          <h2 className="mb-3 px-3 text-xl font-semibold tracking-tight">{t("albums")}</h2>
          <ul className={GRID}>
            {albums.map((album) => (
              <li key={album.id}>
                <MediaCard
                  href={routes.album(album.id)}
                  title={album.title}
                  subtitle={[album.releaseYear, album.artist.name].filter(Boolean).join(" · ")}
                  artwork={album.artwork}
                  seed={album.id}
                  className="w-full"
                />
              </li>
            ))}
          </ul>
        </section>
      )}
      {artists.length > 0 && (
        <section className="mt-8">
          <h2 className="mb-3 px-3 text-xl font-semibold tracking-tight">{t("artists")}</h2>
          <ul className={GRID}>
            {artists.map((artist) => (
              <li key={artist.id}>
                <MediaCard
                  href={routes.artist(artist.id)}
                  title={artist.name}
                  subtitle={t("artist")}
                  artwork={artist.artwork}
                  seed={artist.id}
                  shape="circle"
                  className="w-full"
                />
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}

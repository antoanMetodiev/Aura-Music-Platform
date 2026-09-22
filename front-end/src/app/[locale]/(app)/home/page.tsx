import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { routes } from "@/config/routes";
import {
  albumTracks,
  albums,
  artists,
  madeForYou,
  newReleases,
  recentlyPlayed,
  recommendedPlaylists,
  similarArtists,
  tracksFor,
  userPlaylists,
} from "@/lib/mock/catalog";
import { friendPresence, trendingAmongFriends } from "@/lib/mock/social";
import { getSession } from "@/lib/auth/session";
import { joinArtists } from "@/lib/utils/format";
import { GreetingHeader } from "@/features/home/components/GreetingHeader";
import { HorizontalSection } from "@/features/music/components/HorizontalSection";
import { MediaCard } from "@/features/music/components/MediaCard";
import { QuickAccessGrid, type QuickAccessItem } from "@/features/music/components/QuickAccessGrid";
import { FriendListeningCard } from "@/features/social/components/FriendListeningCard";
import { TrendingTrackRow } from "@/features/social/components/TrendingTrackRow";
import type { AlbumSummary, PlaylistSummary, Track } from "@/types/catalog";

export async function generateMetadata({ params }: PageProps<"/[locale]/home">): Promise<Metadata> {
  const { locale } = (await params) as { locale: Locale };
  const t = await getTranslations({ locale, namespace: "home" });
  return { title: t("title") };
}

/**
 * Home — the personalized feed (FRONTEND.md §4, Project-Info.md §42).
 * Server component; every section is a plain list fed by (for now) mock data.
 */
export default async function HomePage({ params }: PageProps<"/[locale]/home">) {
  const { locale } = (await params) as { locale: Locale };
  setRequestLocale(locale);
  const t = await getTranslations("home");
  const nav = await getTranslations("nav");
  // Guests get the greeting without a name and the "Made for you" section addressed to nobody.
  const displayName = (await getSession())?.user.name ?? null;

  const live = friendPresence.filter((p) => p.status === "listening" && p.track);

  const quickAccess: QuickAccessItem[] = [
    { id: "liked", href: routes.liked, title: nav("likedSongs"), variant: "liked" as const, tracks: tracksFor("liked") },
    ...userPlaylists.map((p) => ({ id: p.id, href: routes.playlist(p.id), title: p.title, artwork: p.artwork, tracks: tracksFor(p.id) })),
    { id: "daily-mix-1", href: routes.playlist("daily-mix-1"), title: "Daily Mix 1", artwork: madeForYou[0]?.artwork, tracks: tracksFor("daily-mix-1") },
    { id: albums.inRainbows.id, href: routes.album(albums.inRainbows.id), title: albums.inRainbows.title, artwork: albums.inRainbows.artwork, tracks: albumTracks(albums.inRainbows.id) },
    { id: albums.currents.id, href: routes.album(albums.currents.id), title: albums.currents.title, artwork: albums.currents.artwork, tracks: albumTracks(albums.currents.id) },
  ].slice(0, 8);

  const trendingContext = trendingAmongFriends.map((item) => item.track);

  return (
    <div className="relative">
      {/* Hero gradient bleeding down from under the top bar */}
      <div aria-hidden className="pointer-events-none absolute inset-x-0 -top-16 h-[420px] bg-gradient-hero opacity-90" />

      <div className="relative flex flex-col gap-10 px-3 pt-4 pb-12 sm:px-5 sm:pt-6">
        <div className="px-3">
          <GreetingHeader name={displayName} liveFriends={live.length} />
        </div>

        <QuickAccessGrid items={quickAccess} className="px-3" />

        {live.length > 0 && (
          <HorizontalSection title={t("sections.friendsListening")} eyebrow={t("sections.liveRightNow")} href={routes.friends}>
            {live.map((presence) => (
              <FriendListeningCard key={presence.user.id} presence={presence} />
            ))}
          </HorizontalSection>
        )}

        <HorizontalSection title={t("sections.recentlyPlayed")} href={routes.recentlyPlayed}>
          {recentlyPlayed.map((item) => (
            <RecentCard
              key={`${kindOf(item)}-${item.id}`}
              item={item}
              labels={{
                song: (artistsText) => t("cards.song", { artists: artistsText }),
                playlist: (owner) => t("cards.playlist", { owner }),
                album: (artist) => t("cards.album", { artist }),
              }}
            />
          ))}
        </HorizontalSection>

        <HorizontalSection title={displayName ? t("sections.madeFor", { name: displayName }) : t("sections.madeForYou")} eyebrow={t("sections.updatedToday")}>
          {madeForYou.map((playlist) => (
            <MediaCard
              key={playlist.id}
              href={routes.playlist(playlist.id)}
              title={playlist.title}
              subtitle={playlist.description}
              artwork={playlist.artwork}
              seed={playlist.id}
              badge={playlist.kind === "mix" ? playlist.title : undefined}
              tracks={tracksFor(playlist.id)}
            />
          ))}
        </HorizontalSection>

        <section className="px-3">
          <header className="mb-3 flex items-end justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground">{t("sections.thisWeek")}</p>
              <h2 className="text-xl font-semibold tracking-tight sm:text-2xl">{t("sections.trending")}</h2>
            </div>
          </header>
          <ol className="grid gap-x-6 rounded-lg border border-border bg-elevated/30 p-2 lg:grid-cols-2">
            {trendingAmongFriends.map(({ track, listeners }, index) => (
              <TrendingTrackRow key={track.id} index={index} track={track} listeners={listeners} context={trendingContext} />
            ))}
          </ol>
        </section>

        <HorizontalSection title={t("sections.newReleases")} eyebrow={t("sections.fromArtistsYouFollow")}>
          {newReleases.map((album) => (
            <MediaCard
              key={album.id}
              href={routes.album(album.id)}
              title={album.title}
              subtitle={`${album.releaseYear} · ${album.artist.name}`}
              artwork={album.artwork}
              seed={album.id}
              tracks={albumTracks(album.id)}
            />
          ))}
        </HorizontalSection>

        <HorizontalSection title={t("sections.becauseYouListened", { artist: artists.radiohead.name })} eyebrow={t("sections.artists")}>
          {similarArtists.map((artist) => (
            <MediaCard
              key={artist.id}
              href={routes.artist(artist.id)}
              title={artist.name}
              subtitle={t("cards.artist")}
              artwork={artist.artwork}
              seed={artist.id}
              shape="circle"
              tracks={tracksFor(artist.id, 5)}
            />
          ))}
        </HorizontalSection>

        <HorizontalSection title={t("sections.recommendedPlaylists")} eyebrow={t("sections.basedOnRecent")}>
          {recommendedPlaylists.map((playlist) => (
            <MediaCard
              key={playlist.id}
              href={routes.playlist(playlist.id)}
              title={playlist.title}
              subtitle={playlist.description}
              artwork={playlist.artwork}
              seed={playlist.id}
              tracks={tracksFor(playlist.id)}
            />
          ))}
        </HorizontalSection>
      </div>
    </div>
  );
}

type RecentItem = Track | AlbumSummary | PlaylistSummary;

function kindOf(item: RecentItem): "track" | "album" | "playlist" {
  if ("durationMs" in item) return "track";
  if ("trackCount" in item) return "playlist";
  return "album";
}

interface RecentCardLabels {
  song: (artists: string) => string;
  playlist: (owner: string) => string;
  album: (artist: string) => string;
}

function RecentCard({ item, labels }: { item: RecentItem; labels: RecentCardLabels }) {
  switch (kindOf(item)) {
    case "track": {
      const track = item as Track;
      return (
        <MediaCard
          href={routes.track(track.id)}
          title={track.title}
          subtitle={labels.song(joinArtists(track.artists))}
          artwork={track.artwork}
          seed={track.id}
          tracks={[track]}
        />
      );
    }
    case "playlist": {
      const playlist = item as PlaylistSummary;
      return (
        <MediaCard
          href={routes.playlist(playlist.id)}
          title={playlist.title}
          subtitle={labels.playlist(playlist.owner.displayName)}
          artwork={playlist.artwork}
          seed={playlist.id}
          tracks={tracksFor(playlist.id)}
        />
      );
    }
    default: {
      const album = item as AlbumSummary;
      return (
        <MediaCard
          href={routes.album(album.id)}
          title={album.title}
          subtitle={labels.album(album.artist.name)}
          artwork={album.artwork}
          seed={album.id}
          tracks={albumTracks(album.id)}
        />
      );
    }
  }
}

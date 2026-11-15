"use client";

import { Heart, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { MediaCard } from "@/features/music/components/MediaCard";
import { useLibraryStore } from "../store/library-store";
import { useCreatePlaylist } from "../hooks/useCreatePlaylist";
import type { LibraryPlaylist } from "../api/libraryApi";
import { LikedCover } from "./CollectionHero";

/** Liked songs, every own playlist, and a "new playlist" tile. Follows the store once it has loaded. */
export function LibraryGrid({ initialPlaylists }: { initialPlaylists: LibraryPlaylist[] }) {
  const t = useTranslations("library");
  const pages = useTranslations("pages");
  const playlists = useLibraryStore((s) => s.playlists) ?? initialPlaylists;
  const createPlaylist = useCreatePlaylist();

  const tile = "flex flex-col gap-3 rounded-lg p-3 transition-colors hover:bg-hover";

  return (
    <ul className="grid grid-cols-2 gap-1 sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-6">
      <li>
        <Link href={routes.liked} className={tile}>
          <div className="aspect-square">
            <LikedCover>
              <Heart className="size-12 fill-white text-white" />
            </LikedCover>
          </div>
          <span className="truncate font-medium">{pages("liked")}</span>
        </Link>
      </li>
      {playlists.map((p) => (
        <li key={p.id}>
          <MediaCard
            href={routes.playlist(p.id)}
            title={p.title}
            subtitle={t("songs", { count: p.trackCount })}
            artwork={p.artwork}
            seed={p.id}
            className="w-full"
          />
        </li>
      ))}
      <li>
        <button type="button" onClick={() => void createPlaylist()} className={`${tile} w-full text-left`}>
          <div className="grid aspect-square place-items-center rounded-md border border-dashed border-border">
            <Plus className="size-10 text-muted-foreground" />
          </div>
          <span className="truncate font-medium">{t("newPlaylist")}</span>
        </button>
      </li>
    </ul>
  );
}

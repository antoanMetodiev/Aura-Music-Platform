"use client";

import { useState } from "react";
import { Globe, ListMusic, Lock, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { getClientAccessToken } from "@/lib/api/token";
import { formatTotalDuration } from "@/lib/utils/format";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { EmptyState } from "@/components/common/EmptyState";
import { Button } from "@/components/ui/button";
import { PlayButton } from "@/features/music/components/PlayButton";
import { TrackRow } from "@/features/music/components/TrackRow";
import { deletePlaylist, removePlaylistItem, updatePlaylist, type PlaylistDetail } from "../api/libraryApi";
import { useLibraryStore } from "../store/library-store";
import { CollectionHero } from "./CollectionHero";

interface PlaylistViewProps {
  detail: PlaylistDetail;
  ownerName: string;
  isOwner: boolean;
}

/** A playlist page. The owner can rename it, flip public/private, delete it and remove tracks. */
export function PlaylistView({ detail, ownerName, isOwner }: PlaylistViewProps) {
  const t = useTranslations("library");
  const router = useRouter();
  const replacePlaylists = useLibraryStore((s) => s.replacePlaylists);
  const refreshPlaylists = useLibraryStore((s) => s.refreshPlaylists);
  const [playlist, setPlaylist] = useState(detail.playlist);
  const [entries, setEntries] = useState(detail.entries);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(detail.playlist.title);

  const tracks = entries.map((e) => e.track);
  const totalMs = tracks.reduce((sum, track) => sum + track.durationMs, 0);

  const save = async (changes: { title?: string; isPublic?: boolean }) => {
    try {
      const updated = await updatePlaylist(await getClientAccessToken(), playlist.id, changes);
      setPlaylist((p) => ({ ...p, title: updated.title, isPublic: updated.isPublic }));
      replacePlaylists((list) => list.map((p) => (p.id === updated.id ? { ...p, title: updated.title, isPublic: updated.isPublic } : p)));
    } catch {
      toast.error(t("saveFailed"));
    }
  };

  const commitTitle = async () => {
    setEditing(false);
    const title = draft.trim();
    if (!title || title === playlist.title) {
      setDraft(playlist.title);
      return;
    }
    await save({ title });
  };

  const remove = async (itemId: string) => {
    const before = entries;
    setEntries((list) => list.filter((e) => e.itemId !== itemId));
    try {
      await removePlaylistItem(await getClientAccessToken(), playlist.id, itemId);
      void refreshPlaylists();
    } catch {
      setEntries(before);
      toast.error(t("removeFailed"));
    }
  };

  const destroy = async () => {
    try {
      await deletePlaylist(await getClientAccessToken(), playlist.id);
      replacePlaylists((list) => list.filter((p) => p.id !== playlist.id));
      toast.success(t("deleted", { title: playlist.title }));
      router.push(routes.library);
    } catch {
      toast.error(t("deleteFailed"));
    }
  };

  const title = isOwner && editing ? (
    <input
      autoFocus
      value={draft}
      maxLength={200}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={() => void commitTitle()}
      onKeyDown={(e) => {
        if (e.key === "Enter") e.currentTarget.blur();
        if (e.key === "Escape") {
          setDraft(playlist.title);
          setEditing(false);
        }
      }}
      aria-label={t("rename")}
      className="w-full rounded-md bg-transparent outline-none ring-2 ring-ring"
    />
  ) : (
    <h1
      className={isOwner ? "cursor-text rounded-md hover:bg-hover/60" : undefined}
      title={isOwner ? t("rename") : undefined}
      onClick={isOwner ? () => setEditing(true) : undefined}
    >
      {playlist.title}
    </h1>
  );

  return (
    <div className="relative">
      <div aria-hidden className="pointer-events-none absolute inset-x-0 -top-16 h-[480px] bg-gradient-hero opacity-90" />
      <div className="relative flex flex-col gap-8 px-3 pt-6 pb-12 sm:px-5 sm:pt-10">
        <CollectionHero
          cover={
            <ArtworkImage
              artwork={entries[0]?.track.artwork ?? entries[0]?.track.album.artwork}
              alt={playlist.title}
              seed={playlist.id}
              sizes="(min-width: 640px) 224px, 192px"
              priority
              className="size-full"
            />
          }
          eyebrow={playlist.isPublic ? t("publicPlaylist") : t("privatePlaylist")}
          title={title}
          meta={
            <>
              {ownerName && <span className="font-medium text-foreground">{ownerName}</span>}
              {ownerName && " · "}
              {t("songs", { count: entries.length })}
              {entries.length > 0 && ` · ${formatTotalDuration(totalMs)}`}
            </>
          }
        />

        <div className="flex items-center gap-3 px-3">
          {entries.length > 0 && <PlayButton tracks={tracks} contextId={playlist.id} size="lg" />}
          {isOwner && (
            <>
              <Button variant="ghost" size="sm" onClick={() => void save({ isPublic: !playlist.isPublic })}>
                {playlist.isPublic ? <Lock /> : <Globe />}
                {playlist.isPublic ? t("makePrivate") : t("makePublic")}
              </Button>
              <Button variant="ghost" size="sm" className="text-muted-foreground hover:text-destructive" onClick={() => void destroy()}>
                <Trash2 /> {t("deletePlaylist")}
              </Button>
            </>
          )}
        </div>

        {entries.length === 0 ? (
          <EmptyState icon={ListMusic} title={t("emptyPlaylist")} description={t("emptyPlaylistHint")} className="min-h-[30vh]" />
        ) : (
          <ol className="flex flex-col">
            {entries.map((entry, index) => (
              <TrackRow
                key={entry.itemId}
                index={index}
                track={entry.track}
                context={tracks}
                onRemove={isOwner ? () => void remove(entry.itemId) : undefined}
              />
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}

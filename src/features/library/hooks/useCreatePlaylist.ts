"use client";

import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useRouter } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { useLibraryStore } from "../store/library-store";

/**
 * Spotify-style: "+" makes "My playlist #N" right away and opens it, where it can be renamed —
 * no dialog in between. Guests are sent to sign in. Optionally seeds the new playlist with tracks.
 */
export function useCreatePlaylist() {
  const t = useTranslations("library");
  const router = useRouter();
  const signedIn = useLibraryStore((s) => s.signedIn);
  const create = useLibraryStore((s) => s.createPlaylist);
  const addToPlaylist = useLibraryStore((s) => s.addToPlaylist);

  return async (trackIds: string[] = [], { open = true }: { open?: boolean } = {}) => {
    if (!signedIn) {
      router.push(routes.login);
      return null;
    }
    try {
      const count = useLibraryStore.getState().playlists?.length ?? 0;
      const playlist = await create(t("defaultPlaylistTitle", { n: count + 1 }));
      if (trackIds.length > 0) await addToPlaylist(playlist.id, trackIds);
      if (open) router.push(routes.playlist(playlist.id));
      else toast.success(t("addedTo", { title: playlist.title }));
      return playlist;
    } catch {
      toast.error(t("createFailed"));
      return null;
    }
  };
}

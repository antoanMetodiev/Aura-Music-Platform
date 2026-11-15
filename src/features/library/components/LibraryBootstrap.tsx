"use client";

import { useEffect } from "react";
import { useLibraryStore } from "../store/library-store";
import type { LibraryPlaylist } from "../api/libraryApi";

/** Hands the server-rendered session state and playlists to the client-side library store. */
export function LibraryBootstrap({ signedIn, playlists }: { signedIn: boolean; playlists: LibraryPlaylist[] | null }) {
  const init = useLibraryStore((s) => s.init);
  useEffect(() => {
    init(signedIn, playlists);
  }, [init, signedIn, playlists]);
  return null;
}

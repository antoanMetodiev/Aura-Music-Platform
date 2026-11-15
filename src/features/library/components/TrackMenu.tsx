"use client";

import { ListPlus, MoreHorizontal, Plus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useLibraryStore } from "../store/library-store";
import { useCreatePlaylist } from "../hooks/useCreatePlaylist";

interface TrackMenuProps {
  trackId: string;
  /** Shown on a playlist the user owns: removes this occurrence. */
  onRemove?: () => void;
  className?: string;
}

/** "⋯" on a track: add to a playlist (or a new one), and — inside an own playlist — remove. */
export function TrackMenu({ trackId, onRemove, className }: TrackMenuProps) {
  const t = useTranslations("library");
  const signedIn = useLibraryStore((s) => s.signedIn);
  const playlists = useLibraryStore((s) => s.playlists) ?? [];
  const addToPlaylist = useLibraryStore((s) => s.addToPlaylist);
  const createPlaylist = useCreatePlaylist();

  const add = async (playlistId: string, title: string) => {
    try {
      await addToPlaylist(playlistId, [trackId]);
      toast.success(t("addedTo", { title }));
    } catch {
      toast.error(t("addFailed"));
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={t("moreOptions")}
        onClick={(e) => e.stopPropagation()}
        className={cn(
          "grid size-8 shrink-0 place-items-center rounded-full text-muted-foreground opacity-0 transition-colors outline-none group-hover:opacity-100 hover:text-foreground focus-visible:opacity-100 data-[popup-open]:opacity-100",
          className,
        )}
      >
        <MoreHorizontal className="size-4" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuSub>
          <DropdownMenuSubTrigger>
            <ListPlus /> {t("addToPlaylist")}
          </DropdownMenuSubTrigger>
          <DropdownMenuSubContent className="max-h-80 w-56 overflow-y-auto">
            <DropdownMenuItem onClick={() => void createPlaylist([trackId], { open: false })}>
              <Plus /> {t("newPlaylist")}
            </DropdownMenuItem>
            {signedIn && playlists.length > 0 && (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuGroup>
                  <DropdownMenuLabel>{t("yourPlaylists")}</DropdownMenuLabel>
                  {playlists.map((p) => (
                    <DropdownMenuItem key={p.id} onClick={() => void add(p.id, p.title)}>
                      <span className="truncate">{p.title}</span>
                    </DropdownMenuItem>
                  ))}
                </DropdownMenuGroup>
              </>
            )}
          </DropdownMenuSubContent>
        </DropdownMenuSub>
        {onRemove && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive" onClick={onRemove}>
              <Trash2 /> {t("removeFromPlaylist")}
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

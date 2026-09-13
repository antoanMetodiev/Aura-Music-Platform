import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { joinArtists } from "@/lib/utils/format";
import { useRelativeTime } from "@/hooks/useRelativeTime";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { UserAvatar } from "@/components/common/UserAvatar";
import { PlayButton } from "@/features/music/components/PlayButton";
import type { FriendPresence } from "@/types/social";
import { PresenceDot } from "./PresenceDot";

/**
 * Wide card for the "Friends are listening" rail on Home — the social hook of Aura.
 * Shows who, what, and lets you jump in with one click.
 */
export function FriendListeningCard({ presence }: { presence: FriendPresence }) {
  const t = useTranslations("presence");
  const common = useTranslations("common");
  const relative = useRelativeTime();
  const { user, track, startedAt } = presence;
  if (!track) return null;

  return (
    <article className="group relative flex w-72 shrink-0 snap-start flex-col gap-4 overflow-hidden rounded-lg border border-border bg-elevated/50 p-4 transition-colors hover:border-border-strong hover:bg-elevated">
      {/* soft artwork glow */}
      <div
        aria-hidden
        className="pointer-events-none absolute -top-16 -right-16 size-48 rounded-full opacity-30 blur-3xl transition-opacity group-hover:opacity-50"
        style={{ background: track.album.artwork?.dominantColor ?? "var(--primary-muted)" }}
      />

      <header className="relative flex items-center gap-3">
        <Link href={routes.profile(user.username)} className="relative shrink-0">
          <UserAvatar user={user} />
          <PresenceDot status="listening" className="absolute -right-0.5 -bottom-0.5" />
        </Link>
        <div className="min-w-0 flex-1">
          <Link href={routes.profile(user.username)} className="block truncate text-sm font-medium hover:underline">
            {user.displayName}
          </Link>
          <p className="text-xs text-muted-foreground">
            {t("listening")}
            {startedAt && ` · ${relative(startedAt)}`}
          </p>
        </div>
      </header>

      <div className="relative flex items-center gap-3">
        <Link href={routes.album(track.album.id)} className="shrink-0">
          <ArtworkImage artwork={track.artwork} alt={track.album.title} seed={track.id} sizes="64px" className="size-16 shadow-lg" />
        </Link>
        <div className="min-w-0 flex-1">
          <Link href={routes.track(track.id)} className="block truncate text-sm font-semibold hover:underline">
            {track.title}
          </Link>
          <p className="truncate text-xs text-muted-foreground">{joinArtists(track.artists)}</p>
          <p className="mt-1 truncate text-[11px] text-subtle-foreground">{track.album.title}</p>
        </div>
        <PlayButton
          tracks={[track]}
          size="sm"
          label={common("playItem", { title: track.title })}
          className="opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100"
        />
      </div>
    </article>
  );
}

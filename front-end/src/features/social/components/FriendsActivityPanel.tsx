"use client";

import { Heart, ListMusic, Play, UserPlus } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { routes } from "@/config/routes";
import { joinArtists } from "@/lib/utils/format";
import { ArtworkImage } from "@/components/common/ArtworkImage";
import { UserAvatar } from "@/components/common/UserAvatar";
import { usePlayerStore } from "@/features/player/store/player-store";
import type { ActivityItem, FriendPresence } from "@/types/social";
import { PresenceDot } from "./PresenceDot";
import { useRelativeTime } from "@/hooks/useRelativeTime";

interface FriendsActivityPanelProps {
  presence: FriendPresence[];
  activity: ActivityItem[];
}

/**
 * Right-panel "Friends" tab: live listening first, then the durable activity feed.
 * Presence is ephemeral (Realtime); activity is persisted (Activity Service).
 */
export function FriendsActivityPanel({ presence, activity }: FriendsActivityPanelProps) {
  const t = useTranslations("panel");
  const live = presence.filter((p) => p.status === "listening" && p.track);
  const feed = activity.filter((a) => a.type !== "LISTENING");

  return (
    <div className="flex flex-col gap-6 p-4">
      <section>
        <SectionLabel count={live.length}>{t("listeningNow")}</SectionLabel>
        <ul className="mt-2 flex flex-col">
          {live.map((item) => (
            <LiveRow key={item.user.id} item={item} />
          ))}
          {live.length === 0 && (
            <li className="rounded-md border border-dashed border-border p-4 text-center text-xs text-muted-foreground">
              {t("noOneListening")}
            </li>
          )}
        </ul>
      </section>

      <section>
        <SectionLabel>{t("activity")}</SectionLabel>
        <ul className="mt-2 flex flex-col">
          {feed.map((item) => (
            <ActivityRow key={item.id} item={item} />
          ))}
        </ul>
      </section>
    </div>
  );
}

function SectionLabel({ children, count }: { children: React.ReactNode; count?: number }) {
  return (
    <h3 className="flex items-center gap-2 text-[11px] font-semibold tracking-[0.14em] uppercase text-subtle-foreground">
      {children}
      {count ? (
        <span className="rounded-full bg-success/15 px-1.5 font-mono text-[10px] leading-4 text-success">
          {count}
        </span>
      ) : null}
    </h3>
  );
}

function LiveRow({ item }: { item: FriendPresence }) {
  const common = useTranslations("common");
  const relative = useRelativeTime();
  const play = usePlayerStore((s) => s.play);
  const track = item.track!;

  return (
    <li className="group -mx-2 flex items-center gap-3 rounded-md p-2 transition-colors hover:bg-hover">
      <Link href={routes.profile(item.user.username)} className="relative shrink-0">
        <UserAvatar user={item.user} />
        <PresenceDot status="listening" className="absolute -right-0.5 -bottom-0.5" />
      </Link>
      <div className="min-w-0 flex-1">
        <p className="truncate text-xs text-muted-foreground">
          <Link href={routes.profile(item.user.username)} className="font-medium text-foreground hover:underline">
            {item.user.displayName}
          </Link>
          {item.startedAt && <span> · {relative(item.startedAt)}</span>}
        </p>
        <p className="truncate text-sm">
          <Link href={routes.track(track.id)} className="font-medium hover:underline">
            {track.title}
          </Link>
          <span className="text-muted-foreground"> — {joinArtists(track.artists)}</span>
        </p>
      </div>
      <button
        type="button"
        aria-label={common("playItem", { title: track.title })}
        onClick={() => play(track, [track])}
        className="relative size-10 shrink-0 overflow-hidden rounded-md"
      >
        <ArtworkImage artwork={track.artwork} alt="" seed={track.id} sizes="40px" className="size-10" />
        <span className="absolute inset-0 grid place-items-center bg-black/55 opacity-0 transition-opacity group-hover:opacity-100">
          <Play className="size-4 fill-current text-white" />
        </span>
      </button>
    </li>
  );
}

function ActivityRow({ item }: { item: ActivityItem }) {
  const t = useTranslations("panel");
  const relative = useRelativeTime();
  const { user } = item;

  const name = () => (
    <Link href={routes.profile(user.username)} className="font-medium text-foreground hover:underline">
      {user.displayName}
    </Link>
  );

  let icon: React.ReactNode = null;
  let body: React.ReactNode;
  let artwork: React.ReactNode = null;

  switch (item.type) {
    case "TRACK_LIKED":
      icon = <Heart className="size-3 fill-current text-destructive" />;
      body = t.rich("activityLiked", {
        name,
        track: () => (
          <Link href={routes.track(item.track!.id)} className="text-foreground hover:underline">
            {item.track!.title}
          </Link>
        ),
      });
      artwork = <ArtworkImage artwork={item.track!.artwork} alt="" seed={item.track!.id} sizes="40px" className="size-10" />;
      break;
    case "PLAYLIST_CREATED":
      icon = <ListMusic className="size-3 text-primary-hover" />;
      body = t.rich("activityPlaylistCreated", {
        name,
        playlist: () => (
          <Link href={routes.playlist(item.playlist!.id)} className="text-foreground hover:underline">
            {item.playlist!.title}
          </Link>
        ),
      });
      artwork = <ArtworkImage artwork={item.playlist!.artwork} alt="" seed={item.playlist!.id} sizes="40px" className="size-10" />;
      break;
    case "FRIEND_ACCEPTED":
      icon = <UserPlus className="size-3 text-success" />;
      body = t.rich("activityFriendAccepted", { name });
      break;
    default:
      body = name();
  }

  return (
    <li className="-mx-2 flex items-center gap-3 rounded-md p-2 transition-colors hover:bg-hover">
      <div className="relative shrink-0">
        <UserAvatar user={user} />
        {icon && (
          <span className="absolute -right-1 -bottom-1 grid size-4 place-items-center rounded-full bg-elevated ring-2 ring-panel">
            {icon}
          </span>
        )}
      </div>
      <div className="min-w-0 flex-1">
        <p className="line-clamp-2 text-sm leading-snug text-muted-foreground">{body}</p>
        <p className="mt-0.5 text-[11px] text-subtle-foreground">{relative(item.occurredAt)}</p>
      </div>
      {artwork}
    </li>
  );
}

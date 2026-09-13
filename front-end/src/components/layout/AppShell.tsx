import { routes } from "@/config/routes";
import { PlaybackEngine } from "@/features/player/components/PlaybackEngine";
import { PlayerBar } from "@/features/player/components/PlayerBar";
import { MiniPlayer } from "@/features/player/components/MiniPlayer";
import type { PlaylistSummary } from "@/types/catalog";
import type { ActivityItem, FriendPresence, UserSummary } from "@/types/social";
import { MobileBottomNav } from "./MobileBottomNav";
import { RightPanel } from "./RightPanel";
import { Sidebar } from "./Sidebar";
import { TopBar } from "./TopBar";

interface AppShellProps {
  user: UserSummary;
  playlists: PlaylistSummary[];
  presence: FriendPresence[];
  activity: ActivityItem[];
  unreadNotifications?: number;
  children: React.ReactNode;
}

/**
 * The frame every authenticated page lives in (FRONTEND.md §3).
 *
 *   desktop:  [sidebar] [topbar + main] [right panel]  /  [player bar]
 *   mobile:   [topbar + main]  /  [mini player]  /  [bottom nav]
 *
 * Server component: it only lays things out; interactivity lives in the children.
 */
export function AppShell({ user, playlists, presence, activity, unreadNotifications, children }: AppShellProps) {
  return (
    <div className="flex h-dvh flex-col bg-background">
      <PlaybackEngine />

      <div className="flex min-h-0 flex-1 gap-2 p-2 max-md:p-0">
        <Sidebar playlists={playlists} />

        <main
          id="main"
          className="scrollbar-thin relative flex min-w-0 flex-1 flex-col overflow-y-auto rounded-xl bg-panel max-md:rounded-none"
        >
          <TopBar user={user} unreadNotifications={unreadNotifications} />
          <div className="flex-1">{children}</div>
        </main>

        <RightPanel presence={presence} activity={activity} />
      </div>

      <PlayerBar />
      <MiniPlayer className="pt-2" />
      <MobileBottomNav profileHref={routes.profile(user.username)} />
    </div>
  );
}

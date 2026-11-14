"use client";

import { ListMusic, Music2, Users, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { type RightPanelTab, useUiStore } from "@/lib/store/ui-store";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { NowPlayingPanel } from "@/features/player/components/NowPlayingPanel";
import { QueuePanel } from "@/features/player/components/QueuePanel";
import { FriendsActivityPanel } from "@/features/social/components/FriendsActivityPanel";
import type { ActivityItem, FriendPresence } from "@/types/social";
import { ResizeHandle } from "./ResizeHandle";

const tabs: { id: RightPanelTab; key: "nowPlaying" | "queue" | "friends"; icon: React.ComponentType<{ className?: string }> }[] = [
  { id: "now-playing", key: "nowPlaying", icon: Music2 },
  { id: "queue", key: "queue", icon: ListMusic },
  { id: "friends", key: "friends", icon: Users },
];

interface RightPanelProps {
  presence: FriendPresence[];
  activity: ActivityItem[];
}

/** Desktop-only side panel (≥ xl). On smaller screens the same content opens as a sheet. */
export function RightPanel({ presence, activity }: RightPanelProps) {
  const t = useTranslations("panel");
  const open = useUiStore((s) => s.rightPanelOpen);
  const tab = useUiStore((s) => s.rightPanelTab);
  const setTab = useUiStore((s) => s.setRightPanelTab);
  const toggle = useUiStore((s) => s.toggleRightPanel);
  const width = useUiStore((s) => s.rightPanelWidth);
  const resizing = useUiStore((s) => s.resizing);

  if (!open) return null;

  const liveCount = presence.filter((p) => p.status === "listening").length;

  return (
    <>
    <ResizeHandle panel="right-panel" className="hidden xl:block" />
    <aside
      style={{ "--panel-w": `${width}px` } as React.CSSProperties}
      className={cn("hidden w-(--panel-w) shrink-0 flex-col rounded-xl bg-panel xl:flex", resizing ? "transition-none" : "transition-[width] duration-200")}
    >
      <div className="flex h-16 items-center gap-1 border-b border-border px-3">
        <div role="tablist" aria-label={t("label")} className="flex flex-1 gap-1">
          {tabs.map(({ id, key, icon: Icon }) => {
            const active = tab === id;
            const label = t(key);
            return (
              <Tooltip key={id}>
                <TooltipTrigger
                  render={
                    <button
                      type="button"
                      role="tab"
                      aria-selected={active}
                      aria-label={label}
                      onClick={() => setTab(id)}
                      className={cn(
                        "relative grid h-9 flex-1 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-hover hover:text-foreground",
                        active && "bg-active text-foreground",
                      )}
                    />
                  }
                >
                  <Icon className="size-[18px]" />
                  {id === "friends" && liveCount > 0 && (
                    <span className="absolute top-1.5 right-2 size-2 rounded-full bg-success ring-2 ring-panel" />
                  )}
                </TooltipTrigger>
                <TooltipContent side="bottom">{label}</TooltipContent>
              </Tooltip>
            );
          })}
        </div>
        <Button variant="ghost" size="icon-sm" aria-label={t("close")} onClick={toggle}>
          <X className="text-muted-foreground" />
        </Button>
      </div>

      <div role="tabpanel" className="scrollbar-thin min-h-0 flex-1 overflow-y-auto">
        {tab === "now-playing" && <NowPlayingPanel />}
        {tab === "queue" && <QueuePanel />}
        {tab === "friends" && <FriendsActivityPanel presence={presence} activity={activity} />}
      </div>
    </aside>
    </>
  );
}

import { Skeleton } from "@/components/common/Skeleton";
import { TrackRowSkeleton } from "@/features/music/components/TrackListSkeleton";

export { TrackListSkeleton } from "@/features/music/components/TrackListSkeleton";

/** Matches the "All" tab's Top result + Songs preview layout. */
export function TopResultAndSongsSkeleton() {
  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
      <div className="rounded-lg border border-border bg-elevated/40 p-5">
        <Skeleton className="mb-3 h-3 w-24" />
        <Skeleton className="size-24 rounded-lg" />
        <Skeleton className="mt-4 h-7 w-3/4" />
        <Skeleton className="mt-2 h-4 w-1/2" />
        <Skeleton className="mt-4 size-14 rounded-full" />
      </div>
      <div className="flex flex-col justify-center gap-1">
        {Array.from({ length: 5 }).map((_, i) => (
          <TrackRowSkeleton key={i} />
        ))}
      </div>
    </div>
  );
}

/** One card placeholder matching `MediaCard`'s footprint. */
function MediaCardSkeleton({ shape = "square" }: { shape?: "square" | "circle" }) {
  return (
    <div className="flex w-40 shrink-0 flex-col gap-3 p-3 sm:w-44">
      <Skeleton className={shape === "circle" ? "aspect-square w-full rounded-full" : "aspect-square w-full"} />
      <Skeleton className="h-4 w-3/4" />
    </div>
  );
}

/** Horizontal rail placeholder, for the "All" tab's Albums/Artists sections. */
export function MediaRailSkeleton({ count = 6, shape = "square" }: { count?: number; shape?: "square" | "circle" }) {
  return (
    <div className="flex gap-1 overflow-hidden">
      {Array.from({ length: count }).map((_, i) => (
        <MediaCardSkeleton key={i} shape={shape} />
      ))}
    </div>
  );
}

/** Wrapping grid placeholder, for the full-width "Albums"/"Artists" filters. */
export function MediaGridSkeleton({ count = 12, shape = "square" }: { count?: number; shape?: "square" | "circle" }) {
  return (
    <div className="flex flex-wrap gap-1">
      {Array.from({ length: count }).map((_, i) => (
        <MediaCardSkeleton key={i} shape={shape} />
      ))}
    </div>
  );
}

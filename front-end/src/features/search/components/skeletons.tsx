import { Skeleton } from "@/components/common/Skeleton";

/** Track row placeholder — same grid columns as `TrackRow` so nothing jumps when real data lands. */
function TrackRowSkeleton() {
  return (
    <div className="grid grid-cols-[1.5rem_2.5rem_1fr_auto] items-center gap-3 px-3 py-2">
      <Skeleton className="size-4 rounded" />
      <Skeleton className="size-10 shrink-0" />
      <div className="flex min-w-0 flex-col gap-1.5">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-3 w-24" />
      </div>
      <Skeleton className="h-3 w-8" />
    </div>
  );
}

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
        {Array.from({ length: 4 }).map((_, i) => (
          <TrackRowSkeleton key={i} />
        ))}
      </div>
    </div>
  );
}

/** Plain vertical list, for the full-width "Songs" filter. */
export function TrackListSkeleton({ rows = 8 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-1">
      {Array.from({ length: rows }).map((_, i) => (
        <TrackRowSkeleton key={i} />
      ))}
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

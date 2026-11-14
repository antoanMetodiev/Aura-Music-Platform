import { Skeleton } from "@/components/common/Skeleton";

/** Track row placeholder — same grid columns as `TrackRow` so nothing jumps when real data lands. */
export function TrackRowSkeleton() {
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

/** Plain vertical list of track row placeholders. */
export function TrackListSkeleton({ rows = 8 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-1">
      {Array.from({ length: rows }).map((_, i) => (
        <TrackRowSkeleton key={i} />
      ))}
    </div>
  );
}

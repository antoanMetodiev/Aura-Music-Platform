import { cn } from "@/lib/utils";

/** A single pulsing placeholder block. Compose into skeletons that mirror the real layout. */
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded-md bg-elevated", className)} />;
}

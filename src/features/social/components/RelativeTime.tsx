import { useRelativeTime } from "@/hooks/useRelativeTime";

/** "3h" / "2d" for an ISO timestamp; usable from server components. */
export function RelativeTime({ iso }: { iso: string }) {
  const relative = useRelativeTime();
  return <time dateTime={iso}>{relative(iso)}</time>;
}

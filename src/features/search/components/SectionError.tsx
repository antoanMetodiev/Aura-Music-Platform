import { WifiOff } from "lucide-react";

/** Compact inline failure state for one independent search section — never a full-page takeover. */
export function SectionError({ message }: { message: string }) {
  return (
    <div className="flex items-center gap-2 rounded-md border border-dashed border-border px-4 py-6 text-sm text-muted-foreground">
      <WifiOff className="size-4 shrink-0" />
      {message}
    </div>
  );
}

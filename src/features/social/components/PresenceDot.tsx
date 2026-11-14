import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import type { PresenceStatus } from "@/types/social";

/** Tiny status indicator; `listening` pulses so live activity reads at a glance. */
export function PresenceDot({ status, className }: { status: PresenceStatus; className?: string }) {
  const t = useTranslations("presence");
  return (
    <span
      role="status"
      aria-label={t(status)}
      title={t(status)}
      className={cn("relative inline-flex size-2.5 shrink-0", className)}
    >
      {status === "listening" && (
        <span className="absolute inset-0 animate-ping rounded-full bg-success/60" />
      )}
      <span
        className={cn(
          "relative inline-flex size-2.5 rounded-full ring-2 ring-panel",
          status === "listening" && "bg-success",
          status === "online" && "bg-primary-hover",
          status === "offline" && "bg-subtle-foreground",
        )}
      />
    </span>
  );
}

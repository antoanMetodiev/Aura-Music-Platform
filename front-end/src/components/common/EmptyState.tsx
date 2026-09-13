import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}

/** Generic "nothing here" / "something's wrong" panel — no results, search unavailable, etc. */
export function EmptyState({ icon: Icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={`flex min-h-[40vh] flex-col items-center justify-center gap-4 px-6 text-center ${className ?? ""}`}>
      <span className="grid size-14 place-items-center rounded-full border border-border bg-elevated">
        <Icon className="size-6 text-muted-foreground" strokeWidth={1.5} />
      </span>
      <div>
        <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
        {description && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
      </div>
      {action}
    </div>
  );
}

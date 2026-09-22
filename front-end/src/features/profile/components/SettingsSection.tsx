import { cn } from "@/lib/utils";

interface SettingsSectionProps {
  title: string;
  description?: string;
  /** Renders the card with a red edge — the delete-account block. */
  destructive?: boolean;
  children: React.ReactNode;
}

/** One card on the profile settings page: heading, optional hint, content. */
export function SettingsSection({ title, description, destructive, children }: SettingsSectionProps) {
  return (
    <section
      className={cn(
        "rounded-xl border bg-elevated/40 p-5 sm:p-6",
        destructive ? "border-destructive/40" : "border-border",
      )}
    >
      <h2 className={cn("text-lg font-semibold tracking-tight", destructive && "text-destructive")}>{title}</h2>
      {description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
      <div className="mt-5">{children}</div>
    </section>
  );
}

/** Green confirmation line under a form after a successful save. */
export function FormSuccess({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="status" className="rounded-lg border border-success/30 bg-success/10 px-3 py-2 text-sm text-success">
      {message}
    </p>
  );
}

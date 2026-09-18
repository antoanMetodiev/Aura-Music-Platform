import { AuraLogo } from "@/components/layout/AuraLogo";

interface AuthCardProps {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  /** Small print under the form — "New to Aura? Create an account" and the like. */
  footer?: React.ReactNode;
}

/** The centered surface every `(auth)` page uses — logo, heading, form, footer link. */
export function AuthCard({ title, subtitle, children, footer }: AuthCardProps) {
  return (
    <div className="w-full max-w-sm rounded-xl border border-border bg-panel p-7 shadow-[0_30px_80px_-40px_rgba(0,0,0,0.9)] backdrop-blur">
      <AuraLogo className="mb-7" />
      <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
      <p className="mt-1.5 text-sm text-muted-foreground">{subtitle}</p>
      <div className="mt-6">{children}</div>
      {footer ? <p className="mt-6 text-center text-sm text-muted-foreground">{footer}</p> : null}
    </div>
  );
}

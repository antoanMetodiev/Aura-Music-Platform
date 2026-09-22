import { useFormatter, useTranslations } from "next-intl";
import type { AppUser } from "@/lib/auth/session";
import { DevicesCard, SignInMethodCard } from "./AccountCards";
import { AvatarEditor } from "./AvatarEditor";
import { DeleteAccount } from "./DeleteAccount";
import { DetailsForm } from "./DetailsForm";
import { EmailForm } from "./EmailForm";
import { PasswordCard } from "./PasswordCard";

interface ProfileSettingsProps {
  user: AppUser;
  emailVerified: boolean;
  /** ISO date the account was created. */
  createdAt: string | null;
  /** Supabase identity providers on the record, primary first: "email", "google". */
  identities: string[];
}

/** Your own /profile page: everything about the account, one card per concern. */
export function ProfileSettings({ user, emailVerified, createdAt, identities }: ProfileSettingsProps) {
  const t = useTranslations("profile");
  const format = useFormatter();

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 pt-6 pb-16 sm:px-6">
      <header>
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{t("title")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {t("subtitle")}
          {createdAt ? ` · ${t("memberSince", { date: format.dateTime(new Date(createdAt), { month: "long", year: "numeric" }) })}` : ""}
        </p>
      </header>

      <AvatarEditor user={user} />
      <DetailsForm user={user} />
      <EmailForm user={user} emailVerified={emailVerified} />
      <PasswordCard user={user} />
      <SignInMethodCard identities={identities} />
      <DevicesCard />
      <DeleteAccount username={user.username} />
    </div>
  );
}

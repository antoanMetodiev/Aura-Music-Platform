import { useFormatter, useTranslations } from "next-intl";
import { UserAvatar } from "@/components/common/UserAvatar";

interface PublicProfileProps {
  name: string;
  username: string;
  image: string | null;
  createdAt: string | null;
}

/** Someone else's profile: what the identity provider knows. Bio, friends and activity come with the Social service. */
export function PublicProfile({ name, username, image, createdAt }: PublicProfileProps) {
  const t = useTranslations("profile.public");
  const format = useFormatter();

  return (
    <div className="relative">
      <div aria-hidden className="pointer-events-none absolute inset-x-0 -top-16 h-[320px] bg-gradient-hero opacity-90" />
      <div className="relative flex flex-col items-center gap-4 px-6 pt-10 pb-12 text-center sm:flex-row sm:items-end sm:text-left">
        <UserAvatar
          user={{ displayName: name, username, avatarUrl: image ?? undefined }}
          className="size-32 shadow-[0_20px_50px_-20px_rgba(0,0,0,0.8)] [&_[data-slot=avatar-fallback]]:text-4xl"
        />
        <div className="min-w-0">
          <p className="text-xs font-semibold tracking-[0.14em] text-subtle-foreground uppercase">{t("title")}</p>
          <h1 className="mt-1 truncate text-4xl font-bold tracking-tight sm:text-5xl">{name}</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            @{username}
            {createdAt ? ` · ${t("memberSince", { date: format.dateTime(new Date(createdAt), { month: "long", year: "numeric" }) })}` : ""}
          </p>
        </div>
      </div>
    </div>
  );
}

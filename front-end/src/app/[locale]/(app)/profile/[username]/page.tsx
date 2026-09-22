import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { findProfileByUsername } from "@/lib/auth/profile";
import { getSession } from "@/lib/auth/session";
import { createSupabaseServerClient } from "@/lib/supabase/server";
import { ProfileSettings } from "@/features/profile/components/ProfileSettings";
import { PublicProfile } from "@/features/profile/components/PublicProfile";

export async function generateMetadata({ params }: PageProps<"/[locale]/profile/[username]">): Promise<Metadata> {
  const { locale, username } = (await params) as { locale: Locale; username: string };
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: `${t("profile")} · @${username}` };
}

/**
 * /profile/[username]: your own → the editable settings (photo, details, email, password, sign-in
 * method, devices, delete); anyone else's → the public card from identity.user_profile. The richer
 * social profile (bio, links, friends) arrives with the Social service.
 */
export default async function Page({ params }: PageProps<"/[locale]/profile/[username]">) {
  const { locale, username } = (await params) as { locale: Locale; username: string };
  setRequestLocale(locale);

  const session = await getSession();
  if (session && session.user.username.toLowerCase() === username.toLowerCase()) {
    // Fresh from Supabase Auth so a confirmation / email change done elsewhere shows up here.
    const supabase = await createSupabaseServerClient();
    const { data } = await supabase.auth.getUser();
    const record = data.user;
    return (
      <ProfileSettings
        user={session.user}
        emailVerified={Boolean(record?.email_confirmed_at)}
        createdAt={record?.created_at ?? session.user.createdAt.toISOString()}
        identities={(record?.identities ?? []).map((i) => i.provider).filter((p): p is string => Boolean(p))}
      />
    );
  }

  const profile = await findProfileByUsername(username);
  if (!profile) notFound();

  return (
    <PublicProfile
      name={profile.displayName}
      username={profile.username}
      image={profile.avatarUrl ?? profile.pictureUrl}
      createdAt={profile.createdAt.toISOString()}
    />
  );
}

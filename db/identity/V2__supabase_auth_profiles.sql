-- Identity moved to Supabase Auth (ADR-012, 2026-09-22). Accounts, passwords, Google sign-in,
-- email verification and sessions live in the `auth` schema Supabase owns; what we keep here is
-- the profile Aura itself defines — username (the /profile/[username] address), display name and
-- the uploaded photo — keyed by auth.users.id. One row per account, created on first sight
-- (`lib/auth/profile.ts` ensureProfile) so Google sign-ups get one too.

CREATE TABLE identity.user_profile (
    user_id       uuid        PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    username      text        NOT NULL,
    display_name  text        NOT NULL,
    -- Uploaded photo (served by /api/avatars/[id]); NULL falls back to picture_url.
    avatar_url    text,
    -- The sign-in provider's picture (Google), kept here so public profiles can show it too.
    picture_url   text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
-- Usernames are unique regardless of case; the app lowercases them anyway.
CREATE UNIQUE INDEX ux_user_profile_username ON identity.user_profile (lower(username));

-- `user_avatar.user_id` was the Auth0 `sub`; it now holds the Supabase user id as text. Rows for
-- Auth0 users cannot map to anything anymore.
DELETE FROM identity.user_avatar;

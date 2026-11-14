-- Aura moved identity to Auth0 (ADR-011, 2026-09-18). The Better Auth tables that used to live in
-- this schema are gone; what remains here is data Auth0 does not hold for us — today only the
-- uploaded profile photo. `user_id` is the Auth0 `sub` ("auth0|…", "google-oauth2|…").

DROP TABLE IF EXISTS "identity"."userAvatar";
DROP TABLE IF EXISTS "identity"."jwks";
DROP TABLE IF EXISTS "identity"."verification";
DROP TABLE IF EXISTS "identity"."account";
DROP TABLE IF EXISTS "identity"."session";
DROP TABLE IF EXISTS "identity"."user";

CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.user_avatar (
    user_id       text        PRIMARY KEY,
    content_type  text        NOT NULL,
    data          bytea       NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);

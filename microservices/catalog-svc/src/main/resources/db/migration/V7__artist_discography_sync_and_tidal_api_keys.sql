-- Continuous artist discography sync: a background worker walks catalog.artists, pulls every track
-- an artist appears on from the provider and persists it. New artists it meets on the way (featured
-- artists, album artists) land in catalog.artists with NULL discography_synced_at and are picked up
-- on a later pass, so the catalog keeps growing on its own.

ALTER TABLE catalog.artists
    ADD COLUMN discography_synced_at    TIMESTAMPTZ,
    ADD COLUMN discography_attempted_at TIMESTAMPTZ,
    ADD COLUMN discography_error        TEXT,
    ADD COLUMN discography_track_count  INTEGER;

CREATE INDEX ix_artists_discography_queue
    ON catalog.artists (discography_attempted_at NULLS FIRST, created_at);

-- TIDAL credentials. Rows here take precedence over the TIDAL_CLIENT_ID/SECRET env vars; several
-- enabled rows are used in rotation (a 429 or a failed token fetch moves to the next one).
-- `disabled_until` is set by the service itself when a key misbehaves; `enabled` is the manual switch.
CREATE TABLE catalog.tidal_api_keys (
    id              UUID PRIMARY KEY,
    label           TEXT,
    client_id       TEXT NOT NULL,
    client_secret   TEXT NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    priority        INTEGER NOT NULL DEFAULT 0,
    disabled_until  TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (client_id)
);

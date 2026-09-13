-- Aura Music Catalog Service — canonical, provider-agnostic music metadata cache.
-- Owned exclusively by catalog-svc (Project-Info.md §6): no other service may touch `catalog.*`.
-- UUIDs are generated application-side (not gen_random_uuid()) so we never depend on a specific
-- Postgres extension being enabled on the target instance.

CREATE SCHEMA IF NOT EXISTS catalog;

-- ── Artists ────────────────────────────────────────────────────────────────────────────────

CREATE TABLE catalog.artists (
    id                  UUID PRIMARY KEY,
    name                TEXT NOT NULL,
    artwork_url         TEXT,
    artwork_width       INTEGER,
    artwork_height      INTEGER,
    popularity          DOUBLE PRECISION NOT NULL DEFAULT 0,
    provider_synced_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per (provider, providerResourceId) an artist is known under (Project-Info.md §13: never
-- a provider id as our primary key). The unique index is what makes upsert-by-provider-ref safe.
CREATE TABLE catalog.artist_provider_refs (
    artist_id             UUID NOT NULL REFERENCES catalog.artists (id) ON DELETE CASCADE,
    provider              TEXT NOT NULL,
    provider_resource_id  TEXT NOT NULL,
    PRIMARY KEY (artist_id, provider)
);
CREATE UNIQUE INDEX ux_artist_provider_ref ON catalog.artist_provider_refs (provider, provider_resource_id);

-- ── Albums ─────────────────────────────────────────────────────────────────────────────────

CREATE TABLE catalog.albums (
    id                  UUID PRIMARY KEY,
    title               TEXT NOT NULL,
    album_type          TEXT NOT NULL DEFAULT 'UNKNOWN',
    release_date        DATE,
    artist_id           UUID REFERENCES catalog.artists (id),
    artwork_url         TEXT,
    artwork_width       INTEGER,
    artwork_height      INTEGER,
    explicit            BOOLEAN NOT NULL DEFAULT FALSE,
    number_of_tracks    INTEGER NOT NULL DEFAULT 0,
    popularity          DOUBLE PRECISION NOT NULL DEFAULT 0,
    provider_synced_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_albums_artist ON catalog.albums (artist_id);

CREATE TABLE catalog.album_provider_refs (
    album_id              UUID NOT NULL REFERENCES catalog.albums (id) ON DELETE CASCADE,
    provider              TEXT NOT NULL,
    provider_resource_id  TEXT NOT NULL,
    PRIMARY KEY (album_id, provider)
);
CREATE UNIQUE INDEX ux_album_provider_ref ON catalog.album_provider_refs (provider, provider_resource_id);

-- ── Tracks ─────────────────────────────────────────────────────────────────────────────────

CREATE TABLE catalog.tracks (
    id                  UUID PRIMARY KEY,
    title               TEXT NOT NULL,
    version             TEXT,
    duration_ms         BIGINT NOT NULL DEFAULT 0,
    isrc                TEXT,
    explicit            BOOLEAN NOT NULL DEFAULT FALSE,
    popularity          DOUBLE PRECISION NOT NULL DEFAULT 0,
    album_id            UUID REFERENCES catalog.albums (id),
    provider_synced_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_tracks_album ON catalog.tracks (album_id);
-- Supports findTracksByIsrc (Project-Info.md §16: ISRC is a strong signal for playback matching).
CREATE INDEX ix_tracks_isrc ON catalog.tracks (isrc) WHERE isrc IS NOT NULL;

CREATE TABLE catalog.track_provider_refs (
    track_id              UUID NOT NULL REFERENCES catalog.tracks (id) ON DELETE CASCADE,
    provider              TEXT NOT NULL,
    provider_resource_id  TEXT NOT NULL,
    PRIMARY KEY (track_id, provider)
);
CREATE UNIQUE INDEX ux_track_provider_ref ON catalog.track_provider_refs (provider, provider_resource_id);

-- Ordered many-to-many: a track can have featured artists; `position` 0 is the primary artist.
CREATE TABLE catalog.track_artists (
    track_id   UUID NOT NULL REFERENCES catalog.tracks (id) ON DELETE CASCADE,
    artist_id  UUID NOT NULL REFERENCES catalog.artists (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    PRIMARY KEY (track_id, artist_id)
);
CREATE INDEX ix_track_artists_track ON catalog.track_artists (track_id, position);

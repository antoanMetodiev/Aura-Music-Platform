-- Aura Recommendation Service — the taste graph over the canonical catalog.
-- Owned exclusively by recommendation-svc (Project-Info.md §6): no other service may touch
-- `recommendation.*`, and nothing here has a foreign key into `catalog.*` on purpose — the artist
-- ids are integration references obtained over catalog-svc's API, not a physical dependency on
-- another service's tables. A deleted catalog artist simply stops being hydrated (see §7: the
-- schemas share a cluster today so that one of them can move out tomorrow).

CREATE SCHEMA IF NOT EXISTS recommendation;

-- ── Artist similarity graph ────────────────────────────────────────────────────────────────

-- One row per directed edge the provider gave us, already resolved to OUR artist ids. Directed
-- because the provider's graph is: Last.fm lists Adele among Sam Smith's similar artists far more
-- readily than the reverse. Reads use both directions (see ix_..._target) with the reverse edge
-- weighted lower, which is how a small artist gets pulled in by a big neighbour at all.
CREATE TABLE recommendation.artist_similarity (
    source_artist_id  UUID NOT NULL,
    target_artist_id  UUID NOT NULL,
    score             DOUBLE PRECISION NOT NULL,   -- provider match, 0..1
    position          INTEGER NOT NULL,            -- the provider's own ordering, 0-based
    provider          TEXT NOT NULL,               -- 'LASTFM'
    fetched_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (source_artist_id, target_artist_id),
    CHECK (source_artist_id <> target_artist_id)
);
CREATE INDEX ix_artist_similarity_source ON recommendation.artist_similarity (source_artist_id, score DESC);
CREATE INDEX ix_artist_similarity_target ON recommendation.artist_similarity (target_artist_id, score DESC);

-- Community tags per artist (Last.fm `artist.getTopTags`), the genre axis of the graph. Serves both
-- "artists that share a tag with this one" and the genre browse grid — no provider call either way.
CREATE TABLE recommendation.artist_tags (
    artist_id  UUID NOT NULL,
    tag        TEXT NOT NULL,                      -- lower-cased, trimmed
    weight     INTEGER NOT NULL DEFAULT 0,         -- provider count, 0..100
    provider   TEXT NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (artist_id, tag)
);
CREATE INDEX ix_artist_tags_tag ON recommendation.artist_tags (tag, weight DESC);

-- ── Worker bookkeeping ─────────────────────────────────────────────────────────────────────

-- One row per artist we know about, whatever the outcome — the graph worker's queue. `popularity`
-- is copied from the catalog so the queue can be worked most-popular-first without asking again:
-- the provider budget (5 req/s) goes to the artists people actually open.
CREATE TABLE recommendation.artist_graph_sync (
    artist_id       UUID PRIMARY KEY,
    name            TEXT NOT NULL,
    popularity      DOUBLE PRECISION NOT NULL DEFAULT 0,
    attempted_at    TIMESTAMPTZ,
    synced_at       TIMESTAMPTZ,
    error           TEXT,
    edge_count      INTEGER,
    tag_count       INTEGER,
    unresolved_count INTEGER
);
-- The queue: never-attempted first, then the least recently attempted; most popular first within each.
CREATE INDEX ix_artist_graph_sync_queue ON recommendation.artist_graph_sync (attempted_at NULLS FIRST, popularity DESC);

-- Where the walk over catalog-svc's /artists/scan is up to. Single row; wraps to the top at the end,
-- picking up artists the catalog discovered since (mirrors playback.catalog_scan_cursor).
CREATE TABLE recommendation.catalog_scan_cursor (
    id                SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    popularity_below  DOUBLE PRECISION NOT NULL,
    after_id          UUID NOT NULL,
    passes            INTEGER NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Names the provider named as similar that our catalog doesn't have. Not an error — a discovery
-- feed: an artist the graph keeps pointing at is one the catalog is missing, and `seen_count` says
-- how loudly. Fed to catalog search deliberately, never automatically (it costs provider quota).
CREATE TABLE recommendation.unresolved_artist_names (
    name_normalized  TEXT PRIMARY KEY,
    name             TEXT NOT NULL,
    seen_count       INTEGER NOT NULL DEFAULT 1,
    last_seen_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_unresolved_artist_names_seen ON recommendation.unresolved_artist_names (seen_count DESC);

-- Aura Playback Resolver Service — resolved external playback sources for canonical tracks.
-- Owned exclusively by playback-svc: no other service may touch `playback.*` (Project-Info.md §6).
-- UUIDs are generated application-side, matching catalog-svc's convention.

CREATE SCHEMA IF NOT EXISTS playback;

-- One row per (track, provider) — Project-Info.md §19. `provider_resource_id`/`title`/`channel_*`
-- are nullable: a NONE `match_method` row records "we searched and found nothing confident enough"
-- with everything else null, purely so a repeat request doesn't re-run a YouTube search we already
-- know fails (§20 quota management).
CREATE TABLE playback.track_sources (
    id                     UUID PRIMARY KEY,
    track_id               UUID NOT NULL,
    provider               TEXT NOT NULL,
    provider_resource_id   TEXT,
    title                  TEXT,
    channel_id             TEXT,
    channel_title          TEXT,
    duration_ms            BIGINT NOT NULL DEFAULT 0,
    match_score            INTEGER NOT NULL DEFAULT 0,
    match_method           TEXT NOT NULL,
    is_verified            BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_track_sources_track_provider ON playback.track_sources (track_id, provider);

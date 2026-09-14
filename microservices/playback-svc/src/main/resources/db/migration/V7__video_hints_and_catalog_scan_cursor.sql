-- Background video-hint discovery: a worker walks the whole catalog and asks MusicBrainz (by ISRC)
-- for a known YouTube link before any YouTube search is ever spent on the track.
--
-- One row per track we've looked at, whatever the outcome — a track that MusicBrainz has no link for
-- today will not have one tomorrow either, so it is never asked about again.
CREATE TABLE playback.video_hints (
    track_id     UUID PRIMARY KEY,
    isrc         TEXT,
    provider     TEXT NOT NULL,               -- 'MUSICBRAINZ'
    outcome      TEXT NOT NULL,               -- MATCHED | REJECTED | NO_LINK | NOT_FOUND | NO_ISRC
    youtube_id   TEXT,                        -- the link MusicBrainz had, whether or not it passed validation
    match_score  INTEGER,                     -- matcher score when a link was validated
    checked_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_video_hints_outcome ON playback.video_hints (outcome, checked_at);

-- Where the walk over catalog-svc's /tracks/scan is up to. Single row; the walk restarts from the
-- beginning once it reaches the end, picking up tracks created since.
CREATE TABLE playback.catalog_scan_cursor (
    id             SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    created_after  TIMESTAMPTZ NOT NULL,
    after_id       UUID NOT NULL,
    passes         INTEGER NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

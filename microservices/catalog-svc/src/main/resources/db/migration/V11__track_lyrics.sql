-- Lyrics cache, one row per track (todo.md §2.3). Providers (LRCLIB now, Musixmatch later) are asked
-- once; a found text is kept for good, a confirmed miss is recorded as provider = 'NONE' so the same
-- song isn't looked up on every play — it's retried only after aura.lyrics.retry-missing-after.

CREATE TABLE catalog.track_lyrics (
    track_id      UUID PRIMARY KEY REFERENCES catalog.tracks (id) ON DELETE CASCADE,
    provider      TEXT NOT NULL,                 -- 'LRCLIB' | 'MUSIXMATCH' | 'NONE' (looked up, nothing found)
    instrumental  BOOLEAN NOT NULL DEFAULT FALSE,
    synced        JSONB,                         -- [{"t": 13900, "text": "..."}, ...] or NULL
    plain         TEXT,
    fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

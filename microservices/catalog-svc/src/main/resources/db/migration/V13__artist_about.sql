-- "About" for an artist: biography, tags, similar artists and outside links, gathered lazily on the
-- first open of the artist page (Last.fm for text/tags/similar/stats, Discogs for links and a
-- fallback profile) and kept for aura.artist-about.refresh-after. A row with everything null is a
-- recorded "asked, nothing found" so the providers aren't hit again on every open.

CREATE TABLE catalog.artist_about (
    artist_id    UUID PRIMARY KEY REFERENCES catalog.artists (id) ON DELETE CASCADE,
    bio          TEXT,
    bio_source   TEXT,                 -- 'LASTFM' | 'DISCOGS'
    bio_url      TEXT,                 -- provider page to credit (Last.fm text is CC BY-SA)
    bio_lang     TEXT,
    listeners    BIGINT,
    playcount    BIGINT,
    tags         JSONB,                -- ["pop", "indie", ...]
    similar_artists JSONB,             -- [{"name": "...", "url": "..."}, ...]
    links        JSONB,                -- [{"type": "instagram", "url": "..."}, ...]
    fetched_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "Fans also like" names from the provider are matched to our own artists by exact (case-insensitive) name.
CREATE INDEX ix_artists_lower_name ON catalog.artists (lower(name));

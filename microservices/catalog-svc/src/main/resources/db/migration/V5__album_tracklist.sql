-- Album track lists (Project-Info.md §14 lazy discovery, album page slice).
-- A track's position inside its album comes from the provider's album-items relationship, not from
-- the track itself, so tracks discovered via search have NULL here until their album is opened.
-- `tracks_synced_at` records when we last pulled an album's full item list, so repeat album opens
-- are served from our own catalog until the metadata TTL passes.

ALTER TABLE catalog.tracks
    ADD COLUMN volume_number INTEGER,
    ADD COLUMN track_number  INTEGER;

ALTER TABLE catalog.albums
    ADD COLUMN tracks_synced_at TIMESTAMPTZ;

CREATE INDEX ix_tracks_album_position ON catalog.tracks (album_id, volume_number, track_number);

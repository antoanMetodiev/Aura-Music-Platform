-- Providers carry the same artist several times (a label uploads a single under a fresh profile):
-- TIDAL has four "Medi"s, four "Lana Del Rey"s. Entries with the same name that share a recording
-- (ISRC) or a release (album) are one artist for us: one of them is canonical and the rest point at
-- it. Reads through an alias serve the canonical; tracks and albums are gathered across the group;
-- search and suggestions list the canonical only. Provider refs stay per row, so each profile's
-- discography keeps syncing under its own TIDAL id.

ALTER TABLE catalog.artists ADD COLUMN canonical_artist_id UUID REFERENCES catalog.artists (id) ON DELETE SET NULL;
CREATE INDEX ix_artists_canonical ON catalog.artists (canonical_artist_id) WHERE canonical_artist_id IS NOT NULL;
-- Grouping duplicates walks lower(name); the index from V13 covers the equality lookup.

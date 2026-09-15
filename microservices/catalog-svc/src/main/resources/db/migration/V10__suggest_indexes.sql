-- Type-ahead suggestions (Project-Info.md §31) hit the catalog on every keystroke, so every
-- predicate they use gets an index:
--   * `t.title ILIKE '%q%'`  → ix_tracks_title_trgm (V4)
--   * `a.name  ILIKE '%q%'`  → ix_artists_name_trgm (V4)
--   * "tracks whose artist matches" walks track_artists from the artist side, which had no index;
--   * ties are broken by popularity, so the sort has one too.
CREATE INDEX IF NOT EXISTS ix_track_artists_artist ON catalog.track_artists (artist_id, track_id);
CREATE INDEX IF NOT EXISTS ix_tracks_popularity    ON catalog.tracks  (popularity DESC);
CREATE INDEX IF NOT EXISTS ix_artists_popularity   ON catalog.artists (popularity DESC);
-- Prefix matches rank first; a plain btree on the lower-cased title/name serves `LIKE 'q%'`.
CREATE INDEX IF NOT EXISTS ix_tracks_title_lower   ON catalog.tracks  (lower(title) text_pattern_ops);
CREATE INDEX IF NOT EXISTS ix_artists_name_lower   ON catalog.artists (lower(name)  text_pattern_ops);

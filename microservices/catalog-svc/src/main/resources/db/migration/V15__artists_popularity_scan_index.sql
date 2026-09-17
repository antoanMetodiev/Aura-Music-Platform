-- Keyset walk over the canonical artists most-popular-first (GET /artists/scan), used by
-- recommendation-svc's similarity-graph worker so the provider budget goes to the artists people
-- actually open. Partial: aliases (V14) are never scanned — a duplicate profile is the same act.
CREATE INDEX ix_artists_popularity_scan ON catalog.artists (popularity DESC, id DESC)
    WHERE canonical_artist_id IS NULL;

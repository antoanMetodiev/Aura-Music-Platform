-- Keyset walk over the whole catalog most-popular-first (GET /tracks/scan?order=popularity), used by
-- playback-svc's video-hint worker so provider calls go to the tracks people will actually play.
CREATE INDEX ix_tracks_popularity_scan ON catalog.tracks (popularity DESC, id DESC);

-- 1) ISRC reuse. 54% of catalog tracks share an ISRC with another track (re-releases, compilations)
--    — same recording, so a YouTube video verified for one is right for all of them. Sources now carry
--    the ISRC so a sibling's verified match can be copied instead of searched for (100 units) or asked
--    about again (MusicBrainz, 1 call/s).
ALTER TABLE playback.track_sources ADD COLUMN isrc TEXT;

-- Backfill from what the hint worker already recorded; live-resolved tracks it hasn't visited yet
-- get their ISRC the next time their source is written.
UPDATE playback.track_sources s
SET    isrc = h.isrc
FROM   playback.video_hints h
WHERE  h.track_id = s.track_id AND h.isrc IS NOT NULL;

CREATE INDEX ix_track_sources_verified_isrc
    ON playback.track_sources (isrc) WHERE isrc IS NOT NULL AND is_verified;

CREATE INDEX ix_video_hints_isrc ON playback.video_hints (isrc) WHERE isrc IS NOT NULL;

-- video_hints.provider now records where the answer came from: 'MUSICBRAINZ' (asked) or 'SIBLING'
-- (copied from another track with the same ISRC). Existing rows are all MusicBrainz answers.

-- 2) Popularity-first walk. The cursor moves from insertion order to (popularity DESC, id DESC); the
--    row is dropped so the next pass starts from the most popular track. Every track already visited
--    is skipped from video_hints, so the restart costs only catalog reads.
ALTER TABLE playback.catalog_scan_cursor DROP COLUMN created_after;
ALTER TABLE playback.catalog_scan_cursor ADD COLUMN popularity_below DOUBLE PRECISION NOT NULL DEFAULT 2;
DELETE FROM playback.catalog_scan_cursor;

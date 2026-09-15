-- video_hints.provider is now the comma-separated list of EVERY source asked about the track, in chain
-- order ('MUSICBRAINZ,DISCOGS'; 'MUSICBRAINZ,SIBLING' = MusicBrainz's answer inherited from a track with
-- the same ISRC). A row whose outcome isn't MATCHED and whose list lacks a source that could still be
-- asked is revisited by the worker, which asks only the missing source and updates the row in place —
-- so the thousands of MusicBrainz misses recorded before Discogs existed get their Discogs turn.

-- Rows written by the first Discogs deployment recorded only the last source asked.
UPDATE playback.video_hints SET provider = 'MUSICBRAINZ,DISCOGS'
WHERE provider = 'DISCOGS' AND isrc IS NOT NULL;

-- Sibling-copied misses predate Discogs: what they inherited was MusicBrainz's answer.
UPDATE playback.video_hints SET provider = 'MUSICBRAINZ,SIBLING'
WHERE provider = 'SIBLING' AND outcome <> 'MATCHED';

-- Start the walk from the most popular track again so the revisits go to what gets played first.
DELETE FROM playback.catalog_scan_cursor;

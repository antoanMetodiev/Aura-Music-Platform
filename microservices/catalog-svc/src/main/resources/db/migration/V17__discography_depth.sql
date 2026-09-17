-- An artist page needs about sixty distinct recordings. A full discography pull fetches every
-- *release* of every track — 1 243 rows for Glass Animals, which are really ~60 songs across singles,
-- albums, deluxe editions and compilations — and takes ~2 minutes at the provider's pace, after which
-- the page throws 99% of it away. Someone waiting in front of the screen should not pay for that.
--
-- So a sync now has a depth. An artist somebody just opened is fetched QUICK (one entry per distinct
-- recording, first page only — a handful of provider calls, a few seconds) and the page fills in
-- immediately; the FULL walk happens later, when nobody is waiting, because the catalog does want
-- every release: album track lists and playback's ISRC-sibling reuse are both built on them.
--
-- NULL = never synced. A QUICK row counts as synced for the UI and as still-pending for the walk.
ALTER TABLE catalog.artists ADD COLUMN discography_depth TEXT;

-- Everything synced before this migration was a full pull.
UPDATE catalog.artists SET discography_depth = 'FULL' WHERE discography_synced_at IS NOT NULL;

-- The walk revisits QUICK rows to deepen them; this keeps that scan off a sequential read.
CREATE INDEX ix_artists_discography_quick ON catalog.artists (discography_attempted_at)
    WHERE discography_depth = 'QUICK';

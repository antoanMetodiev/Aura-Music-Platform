-- Opening an artist page used to pull that artist's whole discography from the provider *inside the
-- request*: the first open of an artist the background walk hadn't reached took minutes (measured:
-- 120s for top-tracks, 39s for albums, while TIDAL was rate-limiting us), and the page sat on its
-- loading skeletons the whole time. Reads now answer from our own catalog immediately and leave a
-- mark here instead; the worker treats a marked artist as the next one to do.
--
-- So this column is a priority signal, not state: "a person is looking at this artist right now,
-- they matter more than the next one by popularity". Cleared once the artist is synced.
ALTER TABLE catalog.artists ADD COLUMN discography_requested_at TIMESTAMPTZ;

-- The queue reads "requested first, most recent request first"; partial because the requested rows
-- are always a handful next to the whole table.
CREATE INDEX ix_artists_discography_requested
    ON catalog.artists (discography_requested_at DESC)
    WHERE discography_requested_at IS NOT NULL;

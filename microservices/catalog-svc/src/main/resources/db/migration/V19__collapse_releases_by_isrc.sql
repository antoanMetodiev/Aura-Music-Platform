-- One track row per recording. The bulk discography walk (worker-svc) used to store every
-- *release* of every track — single, album, deluxe edition, each compilation — which put 395k rows in
-- catalog.tracks for 204k distinct ISRCs (Journey's "Any Way You Want It": twelve rows). Nothing read
-- the extras: search and artist pages already collapse by ISRC (CatalogService.dedupeReleases), an
-- album page pulls its own track list when opened (V5), and playback copies a verified video between
-- ISRC siblings by the ISRC it stores itself, so its rows keyed by a deleted id still serve as donors.
-- The walk now asks the provider for one entry per recording (FINGERPRINT); this removes what it wrote.
--
-- Survivor per ISRC: a release by the track's own primary artist beats a compilation ("Various
-- Artists"), then the provider's popularity, then the oldest row. Every ISRC here is non-null (V1
-- allows NULL but no row has one; the partition keeps such rows apart anyway).

-- ~2.5 minutes over the whole catalog; the pooler must not cut the transaction short.
SET LOCAL statement_timeout = 0;

CREATE TEMP TABLE collapsed_tracks ON COMMIT DROP AS
SELECT id, album_id
FROM (
    SELECT t.id,
           t.album_id,
           row_number() OVER (
               PARTITION BY t.isrc
               ORDER BY EXISTS (SELECT 1 FROM catalog.track_artists ta
                                 WHERE ta.track_id = t.id AND ta.position = 0
                                   AND ta.artist_id = a.artist_id) DESC,
                        t.popularity DESC,
                        t.created_at ASC,
                        t.id
           ) AS rn
    FROM catalog.tracks t
    LEFT JOIN catalog.albums a ON a.id = t.album_id
    WHERE t.isrc IS NOT NULL
) ranked
WHERE rn > 1;

-- track_artists, track_provider_refs and track_lyrics follow by ON DELETE CASCADE.
DELETE FROM catalog.tracks t USING collapsed_tracks c WHERE t.id = c.id;

-- An album that just lost rows is served from our catalog only while tracks_synced_at is fresh
-- (CatalogService.getAlbumTracks); clearing it makes the next open pull the full list again.
UPDATE catalog.albums a
   SET tracks_synced_at = NULL
 WHERE tracks_synced_at IS NOT NULL
   AND a.id IN (SELECT DISTINCT album_id FROM collapsed_tracks WHERE album_id IS NOT NULL);

-- The same release uploaded several times under fresh provider ids ("I Follow Rivers" ×5 singles):
-- of the albums that share a title and artist, the empty copies go when one copy still has tracks.
-- Never-opened albums with a unique title stay — they are a discovery waiting to happen, not a copy.
DELETE FROM catalog.albums dup
 WHERE NOT EXISTS (SELECT 1 FROM catalog.tracks t WHERE t.album_id = dup.id)
   AND EXISTS (SELECT 1
                 FROM catalog.albums keep
                WHERE keep.id <> dup.id
                  AND lower(keep.title) = lower(dup.title)
                  AND keep.artist_id = dup.artist_id
                  AND EXISTS (SELECT 1 FROM catalog.tracks t WHERE t.album_id = keep.id));

-- Cached search result id lists may point at removed rows; they are cheap to rebuild.
DELETE FROM catalog.search_results;

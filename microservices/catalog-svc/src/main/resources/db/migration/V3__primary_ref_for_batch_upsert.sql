-- A unique, provider-derived key on each entity table so a whole search result can be written with
-- one multi-row `INSERT ... ON CONFLICT (primary_ref) DO UPDATE` per table — atomic, race-free, and
-- ~8 round trips per search instead of ~20 per track. `primary_ref` is "PROVIDER:resourceId" of the
-- provider that first discovered the entity; further providers still go in *_provider_refs.

ALTER TABLE catalog.artists ADD COLUMN primary_ref TEXT;
UPDATE catalog.artists a
   SET primary_ref = r.provider || ':' || r.provider_resource_id
  FROM catalog.artist_provider_refs r
 WHERE r.artist_id = a.id;
UPDATE catalog.artists SET primary_ref = 'ORPHAN:' || id WHERE primary_ref IS NULL;
ALTER TABLE catalog.artists ALTER COLUMN primary_ref SET NOT NULL;
CREATE UNIQUE INDEX ux_artists_primary_ref ON catalog.artists (primary_ref);

ALTER TABLE catalog.albums ADD COLUMN primary_ref TEXT;
UPDATE catalog.albums a
   SET primary_ref = r.provider || ':' || r.provider_resource_id
  FROM catalog.album_provider_refs r
 WHERE r.album_id = a.id;
UPDATE catalog.albums SET primary_ref = 'ORPHAN:' || id WHERE primary_ref IS NULL;
ALTER TABLE catalog.albums ALTER COLUMN primary_ref SET NOT NULL;
CREATE UNIQUE INDEX ux_albums_primary_ref ON catalog.albums (primary_ref);

ALTER TABLE catalog.tracks ADD COLUMN primary_ref TEXT;
UPDATE catalog.tracks t
   SET primary_ref = r.provider || ':' || r.provider_resource_id
  FROM catalog.track_provider_refs r
 WHERE r.track_id = t.id;
UPDATE catalog.tracks SET primary_ref = 'ORPHAN:' || id WHERE primary_ref IS NULL;
ALTER TABLE catalog.tracks ALTER COLUMN primary_ref SET NOT NULL;
CREATE UNIQUE INDEX ux_tracks_primary_ref ON catalog.tracks (primary_ref);

-- Until now a discography sync overwrote an artist's artwork with whatever the payload carried, and
-- the artist nested inside a track carries none: opening an artist page filled in their catalogue
-- and made their photo disappear in the same breath. The upsert now keeps the old picture when the
-- new one is absent (see CatalogBatchWriter), but the rows already emptied stay empty — the same
-- sync also stamped provider_synced_at, so nothing considers them stale enough to re-read.
--
-- Clearing that stamp is all it takes: the next read of such an artist goes back to the provider,
-- which does return the artwork, and the corrected upsert keeps it this time. Only artists we
-- actually synced and left without a picture are touched, so this costs one provider call apiece and
-- only for pages someone opens.
UPDATE catalog.artists
   SET provider_synced_at = NULL
 WHERE artwork_url IS NULL
   AND discography_synced_at IS NOT NULL;

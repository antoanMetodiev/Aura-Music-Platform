-- Artists marked failed while the provider was merely rate-limiting us were never actually looked
-- at; the worker now releases those instead of recording a failure. Put the ones already marked
-- back at the front of the queue.

UPDATE catalog.artists
SET discography_error = NULL, discography_attempted_at = NULL
WHERE discography_synced_at IS NULL
  AND discography_error LIKE '%ProviderUnavailableException%';

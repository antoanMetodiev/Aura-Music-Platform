-- Name resolution now tries a loosened spelling as well as the exact one (see ArtistNameKeys):
-- "Travi$ Scott", "Lil' Wayne", "JAY-Z" and "Destiny's Child" were all recorded as artists we don't
-- have, while we had every one of them. Artists synced before that are missing those edges, and
-- would not be asked about again until the 60-day refresh, so their turn comes round again now.
--
-- Cheap to redo: it costs the provider calls again, and nothing else — the rows are replaced in place.
UPDATE recommendation.artist_graph_sync SET attempted_at = NULL, synced_at = NULL, error = NULL;

-- The wishlist was built by the old matching, so most of it is wrong. It refills as the walk goes.
DELETE FROM recommendation.unresolved_artist_names;

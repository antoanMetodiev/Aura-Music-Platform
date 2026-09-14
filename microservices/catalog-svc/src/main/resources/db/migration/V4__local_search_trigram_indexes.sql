-- Local full-text search over what we already have (Project-Info.md §31): a query first returns
-- matches from our own catalog instantly, while the provider is asked in parallel. Trigram GIN
-- indexes make `ILIKE '%…%'` and similarity() ranking fast regardless of catalog size.
-- Supabase ships pg_trgm; it lives in the `extensions` schema there, hence the qualified opclass.

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;

CREATE INDEX ix_tracks_title_trgm  ON catalog.tracks  USING gin (title extensions.gin_trgm_ops);
CREATE INDEX ix_albums_title_trgm  ON catalog.albums  USING gin (title extensions.gin_trgm_ops);
CREATE INDEX ix_artists_name_trgm  ON catalog.artists USING gin (name  extensions.gin_trgm_ops);

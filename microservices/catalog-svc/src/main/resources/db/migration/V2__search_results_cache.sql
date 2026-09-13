-- Provider search results, cached per (normalized query, type) so a repeated search is served from
-- our own catalog instead of hitting TIDAL again (Project-Info.md §14, §20). Only the ordered ids
-- are stored — the entities themselves already live in catalog.tracks/albums/artists.

CREATE TABLE catalog.search_results (
    query_normalized  TEXT NOT NULL,
    search_type       TEXT NOT NULL,
    entity_ids        UUID[] NOT NULL,
    fetched_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (query_normalized, search_type)
);

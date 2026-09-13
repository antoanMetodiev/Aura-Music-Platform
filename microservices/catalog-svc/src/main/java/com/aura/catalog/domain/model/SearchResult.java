package com.aura.catalog.domain.model;

import java.util.List;

/** Result of a catalog search. Lists are already normalized canonical entities. */
public record SearchResult(String query, List<Track> tracks, List<Album> albums, List<Artist> artists) {
    public static SearchResult empty(String query) {
        return new SearchResult(query, List.of(), List.of(), List.of());
    }
}

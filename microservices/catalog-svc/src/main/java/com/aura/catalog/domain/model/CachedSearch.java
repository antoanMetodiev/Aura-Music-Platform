package com.aura.catalog.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One cached provider search: the ordered ids a query returned for one type, and when. */
public record CachedSearch(String normalizedQuery, SearchType type, List<UUID> entityIds, Instant fetchedAt) {
}

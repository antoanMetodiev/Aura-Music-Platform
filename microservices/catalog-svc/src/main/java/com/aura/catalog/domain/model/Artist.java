package com.aura.catalog.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Artist(
        UUID id,
        String name,
        Artwork artwork,
        double popularity,
        /** Set when this row is a provider duplicate of another artist — reads should serve that one (see V14). */
        UUID canonicalArtistId,
        List<ProviderReference> providerReferences,
        Instant providerSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isAlias() {
        return canonicalArtistId != null;
    }

    /** The id reads should be served from: the canonical artist's, or our own when we are it. */
    public UUID canonicalId() {
        return canonicalArtistId == null ? id : canonicalArtistId;
    }
}

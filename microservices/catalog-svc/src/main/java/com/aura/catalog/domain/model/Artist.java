package com.aura.catalog.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Artist(
        UUID id,
        String name,
        Artwork artwork,
        double popularity,
        List<ProviderReference> providerReferences,
        Instant providerSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

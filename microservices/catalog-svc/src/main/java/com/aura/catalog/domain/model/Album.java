package com.aura.catalog.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Album(
        UUID id,
        String title,
        AlbumType type,
        LocalDate releaseDate,
        Artist artist,
        Artwork artwork,
        boolean explicit,
        int numberOfTracks,
        double popularity,
        List<ProviderReference> providerReferences,
        Instant providerSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

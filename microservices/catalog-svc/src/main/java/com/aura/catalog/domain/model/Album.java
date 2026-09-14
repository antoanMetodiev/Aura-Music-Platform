package com.aura.catalog.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code tracksSyncedAt} is when the album's full item list was last pulled from its provider; {@code null} = never. */
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
        Instant tracksSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

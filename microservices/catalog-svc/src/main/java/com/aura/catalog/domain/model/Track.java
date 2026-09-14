package com.aura.catalog.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Canonical track (Project-Info.md §13). `id` is ours; provider ids live only in {@code providerReferences}.
 * Artwork is inherited from the album unless the provider gives a track-specific one.
 * {@code volumeNumber}/{@code trackNumber} are {@code null} until the track's album item list has been synced.
 */
public record Track(
        UUID id,
        String title,
        String version,
        long durationMs,
        String isrc,
        boolean explicit,
        double popularity,
        Album album,
        List<Artist> artists,
        Integer volumeNumber,
        Integer trackNumber,
        List<ProviderReference> providerReferences,
        Instant providerSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public Artist primaryArtist() {
        return artists.isEmpty() ? null : artists.getFirst();
    }

    public Artwork artwork() {
        return album != null ? album.artwork() : null;
    }
}

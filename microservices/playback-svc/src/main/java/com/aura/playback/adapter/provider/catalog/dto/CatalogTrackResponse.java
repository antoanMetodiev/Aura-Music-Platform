package com.aura.playback.adapter.provider.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mirrors just the fields we need from catalog-svc's {@code TrackResponse} (Project-Info.md §17). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogTrackResponse(
        UUID id,
        String title,
        long durationMs,
        String isrc,
        List<ArtistSummary> artists,
        Instant createdAt
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtistSummary(UUID id, String name) {
    }
}

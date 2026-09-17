package com.aura.recommendation.adapter.provider.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Mirrors catalog-svc's {@code TrackResponse} — the fields we pass through to our own callers plus
 * the ones we rank on. Unknown fields are ignored, so catalog-svc can add to its response without
 * breaking us (Project-Info.md §49: integration contracts are additive).
 *
 * <p>Every number is a boxed type on purpose. The same artist appears in catalog's responses in two
 * shapes — the full {@code ArtistResponse} with a popularity, and the {@code ArtistSummaryResponse}
 * nested inside a track without one — and a primitive would make an absent field a deserialization
 * failure rather than the "not included in this shape" it actually means.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogTrackResponse(
        UUID id,
        String title,
        String version,
        Long durationMs,
        String isrc,
        Boolean explicit,
        List<CatalogArtistResponse> artists,
        Album album,
        Artwork artwork,
        Double popularity
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Album(UUID id, String title, CatalogArtistResponse artist, Artwork artwork, Integer releaseYear) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artwork(String url, Integer width, Integer height) {
    }
}

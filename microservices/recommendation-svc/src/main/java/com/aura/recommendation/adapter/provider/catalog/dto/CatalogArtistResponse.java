package com.aura.recommendation.adapter.provider.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Mirrors catalog-svc's {@code ArtistResponse} and its lighter {@code ArtistSummaryResponse} at once —
 * the summary nested inside a track carries no popularity, hence the boxed field (see
 * {@link CatalogTrackResponse}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogArtistResponse(UUID id, String name, CatalogTrackResponse.Artwork artwork, Double popularity) {
}

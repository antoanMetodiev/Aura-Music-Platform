package com.aura.catalog.adapter.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /artists/{id}/about}. Every part is optional. {@code biography.source}/{@code url} must be
 * shown as a credit (Last.fm text is CC BY-SA). {@code similar[].artist} is set only for artists in
 * our catalog — the rest are names to display, not links.
 */
public record ArtistAboutResponse(
        UUID artistId,
        BiographyResponse biography,
        Long listeners,
        Long playcount,
        List<String> tags,
        List<SimilarArtistResponse> similar,
        List<ExternalLinkResponse> links
) {
    public record BiographyResponse(String text, String source, String url, String language) {
    }

    public record SimilarArtistResponse(String name, ArtistSummaryResponse artist) {
    }

    public record ExternalLinkResponse(String type, String url) {
    }
}

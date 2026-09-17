package com.aura.recommendation.adapter.web.dto;

import java.util.List;

/**
 * The home page in one call. {@code key} on a section is a stable identifier ({@code trending},
 * {@code discover}) that the frontend turns into a localized heading.
 */
public record HomeResponse(List<Section> sections, List<TagResponse> genres) {

    public record Section(String key, List<RecommendedTrackResponse> tracks) {
    }
}

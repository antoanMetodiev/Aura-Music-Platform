package com.aura.recommendation.adapter.web.dto;

import java.util.List;

/**
 * A feed plus what seeded it, so a client can render "More like <seed>" without a second call.
 * {@code seedType} is {@code ARTIST}, {@code TRACK}, {@code TAG} or {@code NONE} (trending).
 */
public record FeedResponse(String seedType, String seedId, List<RecommendedTrackResponse> tracks) {
}

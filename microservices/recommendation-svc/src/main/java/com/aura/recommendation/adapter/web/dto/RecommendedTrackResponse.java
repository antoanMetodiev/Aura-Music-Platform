package com.aura.recommendation.adapter.web.dto;

import com.aura.recommendation.domain.model.Reason;
import com.aura.recommendation.domain.model.TrackRef;

/**
 * One recommendation. {@code track} is catalog-svc's own track shape, unchanged, so the frontend maps
 * it with the same code it maps a search result with; {@code reason} is structured for the UI to
 * localize ("Because you like X"), never a sentence written here.
 */
public record RecommendedTrackResponse(TrackRef track, double score, Reason reason, boolean playable) {
}

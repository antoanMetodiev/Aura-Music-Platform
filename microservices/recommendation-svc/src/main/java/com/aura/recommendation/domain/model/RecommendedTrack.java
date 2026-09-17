package com.aura.recommendation.domain.model;

/**
 * @param playable whether playback-svc already has a verified source for it. Unplayable tracks are
 *                 normally dropped ({@code aura.recommendations.drop-unplayable}); the flag survives
 *                 so that when playback-svc is down and nothing can be filtered, the caller still knows.
 */
public record RecommendedTrack(TrackRef track, double score, Reason reason, boolean playable) {
}

package com.aura.playback.domain.model;

/**
 * One raw search result from a video-based playback provider, before {@link ScoredCandidate}
 * scoring. Deliberately provider-agnostic in shape even though YouTube is the only implementation
 * today (Project-Info.md §16: the resolver's business logic must not know it's talking to YouTube).
 */
public record VideoCandidate(
        String providerResourceId,
        String title,
        String description,
        String channelId,
        String channelTitle,
        long durationMs,
        boolean embeddable
) {
}

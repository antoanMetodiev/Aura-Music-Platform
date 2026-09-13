package com.aura.playback.domain.model;

/** A {@link VideoCandidate} after {@code TrackMatcher} scoring (Project-Info.md §18). */
public record ScoredCandidate(VideoCandidate candidate, int score) {
}

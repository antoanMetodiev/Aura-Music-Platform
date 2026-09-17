package com.aura.recommendation.domain.model;

/** A community tag on an artist. {@code weight} is the provider's count, 0..100. */
public record ArtistTag(String tag, int weight) {
}

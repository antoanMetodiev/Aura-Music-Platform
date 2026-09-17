package com.aura.recommendation.domain.port;

/** A community tag as the provider reports it. {@code count} is their 0..100 weight. */
public record ProviderTag(String name, int count) {
}

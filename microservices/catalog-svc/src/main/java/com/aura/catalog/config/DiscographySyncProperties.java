package com.aura.catalog.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Continuous artist discography sync (see {@code ArtistDiscographyService}). */
@Validated
@ConfigurationProperties(prefix = "aura.catalog.discography-sync")
public record DiscographySyncProperties(
        @DefaultValue("true") boolean enabled,
        /** Pause after finishing one artist before claiming the next — the provider rate limiter, in effect. */
        @NotNull @DefaultValue("2s") Duration delayBetweenArtists,
        /** Pause when the queue is empty before looking again. */
        @NotNull @DefaultValue("60s") Duration idleDelay,
        /** A synced artist is pulled again after this long, so new releases show up. */
        @NotNull @DefaultValue("30d") Duration refreshAfter,
        /** A failed artist is retried after this long. */
        @NotNull @DefaultValue("1h") Duration retryAfter
) {
}

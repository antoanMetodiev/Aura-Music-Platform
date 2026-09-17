package com.aura.worker.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Pacing of the taste-graph build: two Last.fm calls per artist, on this service's own key. */
@Validated
@ConfigurationProperties(prefix = "aura.workers.graph")
public record GraphWorkerProperties(
        @DefaultValue("true") boolean enabled,
        /** Pause between artists, on top of the provider throttle. */
        @NotNull @DefaultValue("200ms") Duration delayBetweenArtists,
        /** Pause after a seeding round that found nothing new — the whole catalog is queued. */
        @NotNull @DefaultValue("10m") Duration idleDelay,
        /** Pause when the provider or recommendation-svc is unavailable. */
        @NotNull @DefaultValue("2m") Duration backoffOnOutage,
        /** Neighbours requested per artist. The provider returns them ordered, so this is a depth choice. */
        @DefaultValue("60") int similarArtistsRequested
) {
}

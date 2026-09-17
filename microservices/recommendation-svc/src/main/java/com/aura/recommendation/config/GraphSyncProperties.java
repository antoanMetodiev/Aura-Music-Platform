package com.aura.recommendation.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * The background worker that builds the taste graph (see {@code ArtistGraphService}). Two provider
 * calls per artist at the configured pace is the entire cost of this service's intelligence.
 */
@Validated
@ConfigurationProperties(prefix = "aura.recommendations.graph-sync")
public record GraphSyncProperties(
        @DefaultValue("true") boolean enabled,
        /** Artists fetched from catalog-svc per seeding step — cheap, no provider calls involved. */
        @DefaultValue("200") int seedPageSize,
        /** Pause between artist syncs, on top of the provider throttle. */
        @NotNull @DefaultValue("200ms") Duration delayBetweenArtists,
        /** Pause when the whole catalog's graph is fresh and there is nothing to seed. */
        @NotNull @DefaultValue("10m") Duration idleDelay,
        /** An artist's graph is asked for again after this long — tastes and the provider's data move slowly. */
        @NotNull @DefaultValue("60d") Duration refreshAfter,
        /** Pause after a failed step (provider outage, catalog-svc down). */
        @NotNull @DefaultValue("2m") Duration backoffOnOutage,
        /** Neighbours requested per artist. The provider returns them ordered, so this is a depth choice. */
        @DefaultValue("60") int similarArtistsRequested
) {
}

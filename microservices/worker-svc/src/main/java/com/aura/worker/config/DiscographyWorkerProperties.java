package com.aura.worker.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Pacing of the artist discography sync. This is the knob that used to be shared with catalog-svc's
 * user-facing traffic; on this service's own credentials it can be turned up or down without anyone
 * waiting on a search because of it.
 */
@Validated
@ConfigurationProperties(prefix = "aura.workers.discography")
public record DiscographyWorkerProperties(
        @DefaultValue("true") boolean enabled,
        /**
         * Pause after finishing one artist before claiming the next. It was 2s while this walk shared
         * catalog-svc's TIDAL credentials, because every request it made was one a user might be
         * waiting behind. On its own key there is nobody to be polite to — the adaptive throttle
         * handles the provider itself, and a 429 here costs a pause in this process and nowhere else.
         */
        @NotNull @DefaultValue("500ms") Duration delayBetweenArtists,
        /** Pause when the queue is empty before asking again. */
        @NotNull @DefaultValue("60s") Duration idleDelay,
        /** Pause when the provider or catalog-svc is unavailable. */
        @NotNull @DefaultValue("2m") Duration backoffOnOutage
) {
}

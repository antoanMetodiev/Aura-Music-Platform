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
         * Pause after finishing one artist before claiming the next. On this service's own key there
         * is nobody to be polite to — the adaptive throttle handles the provider itself, and a 429
         * here costs a pause in this process and nowhere else.
         */
        @NotNull @DefaultValue("500ms") Duration delayBetweenArtists,
        /** Pause when the provider or catalog-svc is unavailable. */
        @NotNull @DefaultValue("2m") Duration backoffOnOutage,
        /**
         * How often to ask for work. The queue only ever holds artists somebody has just opened, so
         * it is empty nearly all the time — the cost of an empty claim is one indexed query, and what
         * it buys is a page that fills within seconds of being opened.
         */
        @NotNull @DefaultValue("1s") Duration pollInterval
) {
}

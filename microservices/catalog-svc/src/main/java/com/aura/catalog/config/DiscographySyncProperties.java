package com.aura.catalog.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Queue semantics for the artist discography sync — how long an answer stays good and how soon a
 * failure is retried. The pacing of the sync itself (how fast artists are claimed, what happens on a
 * provider outage) is worker-svc's business now, along with the provider credentials; this service
 * only keeps the queue and writes what comes back.
 */
@Validated
@ConfigurationProperties(prefix = "aura.catalog.discography-sync")
public record DiscographySyncProperties(
        /** A synced artist is pulled again after this long, so new releases show up. */
        @NotNull @DefaultValue("30d") Duration refreshAfter,
        /** A failed artist is retried after this long. */
        @NotNull @DefaultValue("1h") Duration retryAfter
) {
}

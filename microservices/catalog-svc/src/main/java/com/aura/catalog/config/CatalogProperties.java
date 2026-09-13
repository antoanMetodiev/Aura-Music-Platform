package com.aura.catalog.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Catalog behaviour knobs (Project-Info.md §14 TTL strategy). */
@Validated
@ConfigurationProperties(prefix = "aura.catalog")
public record CatalogProperties(
        /** After this age a locally cached entity is refreshed from its provider on the next read. */
        @NotNull @DefaultValue("7d") Duration metadataTtl,
        /**
         * How long a cached provider search (the ordered result ids for a query) is served without
         * asking the provider again. Past it the cached list is still served immediately and refreshed
         * in the background (stale-while-revalidate), so a query never waits on TIDAL twice.
         */
        @NotNull @DefaultValue("24h") Duration searchTtl,
        /** In-memory (Caffeine) copy of hydrated search results, in front of the Postgres cache. */
        @NotNull @DefaultValue("10m") Duration searchMemoryTtl,
        @Min(1) @DefaultValue("10") int defaultSearchLimit,
        @Min(1) @DefaultValue("20") int maxSearchLimit
) {
}

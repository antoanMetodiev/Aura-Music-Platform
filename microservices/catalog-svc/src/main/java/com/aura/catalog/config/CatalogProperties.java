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
        @Min(1) @DefaultValue("10") int defaultSearchLimit,
        @Min(1) @DefaultValue("20") int maxSearchLimit
) {
}

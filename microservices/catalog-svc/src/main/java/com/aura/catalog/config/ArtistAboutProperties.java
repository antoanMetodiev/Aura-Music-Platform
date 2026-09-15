package com.aura.catalog.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Artist "About" cache knobs; provider-specific settings live next to each adapter ({@code music.providers.lastfm.*}, {@code ...discogs.*}). */
@Validated
@ConfigurationProperties(prefix = "aura.artist-about")
public record ArtistAboutProperties(
        /** A stored About is refreshed from the providers after this long (stats move, bios get edited). */
        @NotNull @DefaultValue("30d") Duration refreshAfter,
        /** An artist no provider knew is asked about again only after this long. */
        @NotNull @DefaultValue("30d") Duration retryMissingAfter
) {
}

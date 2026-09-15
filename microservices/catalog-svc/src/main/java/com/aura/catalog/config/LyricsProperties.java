package com.aura.catalog.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Lyrics lookup knobs (todo.md §2.4). Provider-specific settings live next to each adapter ({@code aura.lyrics.lrclib.*}). */
@Validated
@ConfigurationProperties(prefix = "aura.lyrics")
public record LyricsProperties(
        /** Which {@code LyricsProvider} adapter is active: {@code lrclib} or {@code none}. */
        @NotBlank @DefaultValue("lrclib") String provider,
        /** A track the provider had nothing for is asked about again only after this long. */
        @NotNull @DefaultValue("30d") Duration retryMissingAfter
) {
}

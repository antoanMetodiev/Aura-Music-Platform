package com.aura.playback.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Continuous video-hint discovery worker (see {@code VideoHintService}). */
@Validated
@ConfigurationProperties(prefix = "playback.video-hints")
public record VideoHintProperties(
        @DefaultValue("true") boolean enabled,
        /** Tracks fetched from catalog-svc per step. Each unchecked track with an ISRC costs one MusicBrainz call (~1s). */
        @DefaultValue("25") int pageSize,
        /** Pause between pages. */
        @NotNull @DefaultValue("1s") Duration delayBetweenPages,
        /** Pause after a full pass over the catalog before starting the next one. */
        @NotNull @DefaultValue("10m") Duration idleAfterFullPass,
        /** Pause when catalog-svc, MusicBrainz or YouTube is unavailable (incl. exhausted quota). */
        @NotNull @DefaultValue("5m") Duration backoffOnOutage
) {
}

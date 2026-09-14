package com.aura.playback.adapter.provider.musicbrainz;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * MusicBrainz asks for a descriptive User-Agent with contact info and allows about one request per
 * second per client — anything faster is answered with 503s.
 */
@Validated
@ConfigurationProperties(prefix = "music.providers.musicbrainz")
public record MusicBrainzProperties(
        @DefaultValue("true") boolean enabled,
        @NotBlank @DefaultValue("https://musicbrainz.org/ws/2") String apiBaseUrl,
        @NotBlank @DefaultValue("Aura-Music-Platform/0.1 (https://github.com/aura-music)") String userAgent,
        /** Minimum spacing between two MusicBrainz calls from this process. */
        @DefaultValue("1100ms") Duration minRequestInterval
) {
}

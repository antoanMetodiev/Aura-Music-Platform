package com.aura.playback.adapter.provider.discogs;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Discogs (https://www.discogs.com/developers): 60 requests/minute with a personal access token, 25
 * without; asks for a descriptive User-Agent. Release and master pages carry the community-curated
 * "Videos" list — mostly the label's official uploads.
 */
@Validated
@ConfigurationProperties(prefix = "music.providers.discogs")
public record DiscogsProperties(
        @DefaultValue("true") boolean enabled,
        @NotBlank @DefaultValue("https://api.discogs.com") String apiBaseUrl,
        /** Personal access token from Settings → Developers. Blank = unauthenticated (25/min). */
        @DefaultValue("") String token,
        @NotBlank @DefaultValue("Aura-Music-Platform/0.1 (https://github.com/aura-music)") String userAgent,
        /** Minimum spacing between two Discogs calls from this process (60/min with a token → just over 1s). */
        @DefaultValue("1100ms") Duration minRequestInterval,
        /** Search results considered when picking the release to open. */
        @DefaultValue("5") int searchResults
) {
    public boolean authenticated() {
        return token != null && !token.isBlank();
    }
}

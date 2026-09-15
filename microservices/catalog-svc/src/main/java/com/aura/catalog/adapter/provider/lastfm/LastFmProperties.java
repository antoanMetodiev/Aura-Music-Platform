package com.aura.catalog.adapter.provider.lastfm;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Last.fm (https://www.last.fm/api): free API key, 5 requests/second per IP, non-commercial by
 * default (commercial use needs an agreement). Biographies are community-written, CC BY-SA, and must
 * be credited with a link back — the UI shows the {@code bio.url} we store.
 */
@Validated
@ConfigurationProperties(prefix = "music.providers.lastfm")
public record LastFmProperties(
        @DefaultValue("true") boolean enabled,
        @NotBlank @DefaultValue("https://ws.audioscrobbler.com/2.0/") String apiBaseUrl,
        /** From https://www.last.fm/api/account/create. Blank disables the provider. */
        @DefaultValue("") String apiKey,
        @NotBlank @DefaultValue("Aura-Music-Platform/0.1 (dev)") String userAgent
) {
    public boolean configured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}

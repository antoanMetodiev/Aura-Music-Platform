package com.aura.playback.adapter.provider.youtube;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** YouTube Data API v3 settings (Project-Info.md §37 compliance flags live under the same prefix). */
@Validated
@ConfigurationProperties(prefix = "music.providers.youtube")
public record YouTubeProperties(
        @DefaultValue("true") boolean enabled,
        String apiKey,
        @NotBlank @DefaultValue("https://www.googleapis.com/youtube/v3") String apiBaseUrl,
        /** "Music" category — keeps search.list from returning unrelated video results. */
        @DefaultValue("10") String musicCategoryId,
        @DefaultValue("10") int maxSearchResults,
        /** ISO 3166-1 alpha-2; nudges relevance the same way TIDAL's countryCode does. */
        @DefaultValue("BG") String regionCode
) {
}

package com.aura.catalog.adapter.provider.tidal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * TIDAL provider settings (Project-Info.md §37 compliance flags live under the same prefix).
 * Secrets come from the environment only — never commit them.
 */
@Validated
@ConfigurationProperties(prefix = "music.providers.tidal")
public record TidalProperties(
        @DefaultValue("true") boolean enabled,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank @DefaultValue("https://openapi.tidal.com/v2") String apiBaseUrl,
        @NotBlank @DefaultValue("https://auth.tidal.com/v1/oauth2/token") String tokenUrl,
        /** ISO 3166-1 alpha-2. Drives availability and search relevance on TIDAL's side. */
        @NotBlank @DefaultValue("BG") String countryCode,
        /** Refresh the access token this long before TIDAL says it expires. */
        @NotNull @DefaultValue("60s") Duration tokenRefreshSkew,
        /** TIDAL caps `filter[id]` lists at 20 (OpenAPI spec). */
        @DefaultValue("20") int batchSize
) {
}

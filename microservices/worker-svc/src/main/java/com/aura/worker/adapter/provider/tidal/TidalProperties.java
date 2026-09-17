package com.aura.worker.adapter.provider.tidal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * This service's TIDAL settings (Project-Info.md §37 compliance flags live under the same prefix).
 * The credential pair comes from the environment and only from there — see
 * {@link TidalCredentialsSource} for why this service deliberately ignores catalog-svc's key table.
 * Never commit secrets.
 */
@Validated
@ConfigurationProperties(prefix = "music.providers.tidal")
public record TidalProperties(
        @DefaultValue("true") boolean enabled,
        String clientId,
        String clientSecret,
        @NotBlank @DefaultValue("https://openapi.tidal.com/v2") String apiBaseUrl,
        @NotBlank @DefaultValue("https://auth.tidal.com/v1/oauth2/token") String tokenUrl,
        /** ISO 3166-1 alpha-2. Drives availability and search relevance on TIDAL's side. */
        @NotBlank @DefaultValue("BG") String countryCode,
        /** Refresh the access token this long before TIDAL says it expires. */
        @NotNull @DefaultValue("60s") Duration tokenRefreshSkew,
        /** TIDAL caps `filter[id]` lists at 20 (OpenAPI spec). */
        @DefaultValue("20") int batchSize,
        /**
         * Outbound throttle for the whole process. TIDAL answers a burst of ~15 concurrent calls with
         * 429 + Retry-After for a third of them, so calls are spaced out client-side instead.
         */
        @DefaultValue("3") int maxRequestsPerSecond,
        /** Calls in flight at once — TIDAL's bucket is drained by in-flight calls, not just call starts. */
        @DefaultValue("2") int maxConcurrentRequests,
        /** For an artist's full tracklist: NONE = every release of every track, FINGERPRINT = one per distinct recording. */
        @DefaultValue("NONE") String artistTracksCollapseBy,
        /** Pages of the collapsed tracklist a quick pull takes. One page is ~20 recordings. */
        @DefaultValue("3") int quickArtistTrackPages
) {
    /** Without both halves of the pair there is nothing to authenticate with, so the worker stays down. */
    public boolean configured() {
        return enabled && clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}

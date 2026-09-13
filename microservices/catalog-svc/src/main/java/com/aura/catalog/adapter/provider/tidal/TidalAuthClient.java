package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.TidalTokenResponse;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.service.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;

/**
 * OAuth2 client-credentials token source for TIDAL. One token per process, refreshed ahead of expiry.
 * When we run several instances the token moves to Redis (Project-Info.md §30); the interface stays.
 */
@Component
public class TidalAuthClient {

    private static final Logger log = LoggerFactory.getLogger(TidalAuthClient.class);

    private final RestClient restClient;
    private final TidalProperties properties;
    private final Clock clock;

    private volatile CachedToken cached;

    public TidalAuthClient(RestClient.Builder builder, TidalProperties properties, Clock clock) {
        this.restClient = builder.clone().build();
        this.properties = properties;
        this.clock = clock;
    }

    public String accessToken() {
        CachedToken token = cached;
        if (token != null && token.isValid(clock.instant())) return token.value();
        return refresh();
    }

    /** Drop the cached token, e.g. after a 401 — the next call will fetch a fresh one. */
    public void invalidate() {
        cached = null;
    }

    private synchronized String refresh() {
        CachedToken token = cached;
        if (token != null && token.isValid(clock.instant())) return token.value();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        try {
            TidalTokenResponse response = restClient.post()
                    .uri(properties.tokenUrl())
                    .headers(h -> h.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TidalTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new ProviderUnavailableException(Provider.TIDAL, new IllegalStateException("empty token response"));
            }
            Instant expiresAt = clock.instant()
                    .plusSeconds(response.expiresInSeconds())
                    .minus(properties.tokenRefreshSkew());
            cached = new CachedToken(response.accessToken(), expiresAt);
            log.info("Obtained TIDAL access token, valid until {}", expiresAt);
            return response.accessToken();
        } catch (RestClientException e) {
            throw new ProviderUnavailableException(Provider.TIDAL, e);
        }
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isValid(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}

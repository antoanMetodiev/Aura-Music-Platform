package com.aura.worker.adapter.provider.tidal;

import com.aura.worker.adapter.provider.tidal.dto.TidalTokenResponse;
import com.aura.worker.domain.model.Provider;
import com.aura.worker.domain.service.ProviderUnavailableException;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2 client-credentials token source for TIDAL, one cached token per credential pair from
 * {@link TidalCredentialsSource}. Refreshed ahead of expiry. When we run several instances the
 * tokens move to Redis (Project-Info.md §30); the interface stays.
 */
@Component
public class TidalAuthClient {

    private static final Logger log = LoggerFactory.getLogger(TidalAuthClient.class);

    private final RestClient restClient;
    private final TidalProperties properties;
    private final TidalCredentialsSource credentials;
    private final Clock clock;

    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public TidalAuthClient(RestClient.Builder builder, TidalProperties properties,
                           TidalCredentialsSource credentials, Clock clock) {
        this.restClient = builder.clone().build();
        this.properties = properties;
        this.credentials = credentials;
        this.clock = clock;
    }

    /** A bearer token for the credentials currently in rotation. */
    public Token accessToken() {
        TidalCredentialsSource.Credentials current = credentials.current();
        CachedToken token = tokens.get(current.clientId());
        if (token != null && token.isValid(clock.instant())) return new Token(token.value(), current);
        return new Token(refresh(current), current);
    }

    /** Drop the cached token for these credentials, e.g. after a 401 — the next call fetches a fresh one. */
    public void invalidate(TidalCredentialsSource.Credentials of) {
        tokens.remove(of.clientId());
    }

    /** Bench these credentials and move to the next pair; with a single pair nothing changes (its token stays cached). */
    public void switchKeyIfPossible(TidalCredentialsSource.Credentials failed, String reason) {
        if (credentials.poolSize() > 1) credentials.penalize(failed, reason);
    }

    private synchronized String refresh(TidalCredentialsSource.Credentials of) {
        CachedToken token = tokens.get(of.clientId());
        if (token != null && token.isValid(clock.instant())) return token.value();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        try {
            TidalTokenResponse response = restClient.post()
                    .uri(properties.tokenUrl())
                    .headers(h -> h.setBasicAuth(of.clientId(), of.clientSecret()))
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
            tokens.put(of.clientId(), new CachedToken(response.accessToken(), expiresAt));
            credentials.markUsed(of);
            log.info("Obtained TIDAL access token with key '{}', valid until {}", of.label(), expiresAt);
            return response.accessToken();
        } catch (RestClientException e) {
            credentials.penalize(of, "token request failed: " + e.getMessage());
            throw new ProviderUnavailableException(Provider.TIDAL, e);
        }
    }

    public record Token(String value, TidalCredentialsSource.Credentials credentials) {
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isValid(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}

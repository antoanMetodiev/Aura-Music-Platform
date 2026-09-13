package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.JsonApiDocument;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.service.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Thin, resilient HTTP layer over the TIDAL JSON:API. Knows URLs, auth headers, retries and the
 * circuit breaker — nothing about our domain. Returns raw {@link JsonApiDocument}s for the mapper.
 *
 * Resilience (Project-Info.md §48): timeouts come from {@code spring.http.clients.*}; transient failures
 * (429, 5xx, I/O) are retried with exponential backoff; the circuit breaker sits outside the retry so a
 * dead provider stops costing us latency. 404 is a normal "not found", never a failure.
 */
@Component
public class TidalApiClient {

    private static final Logger log = LoggerFactory.getLogger(TidalApiClient.class);
    private static final String JSON_API = "application/vnd.api+json";

    private final RestClient restClient;
    private final TidalAuthClient auth;
    private final TidalProperties properties;
    private final RetryTemplate retry;
    private final CircuitBreaker circuitBreaker;

    public TidalApiClient(RestClient.Builder builder,
                          TidalAuthClient auth,
                          TidalProperties properties,
                          CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, JSON_API)
                .build();
        this.auth = auth;
        this.properties = properties;
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(2)
                .delay(Duration.ofMillis(250))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(2))
                .jitter(Duration.ofMillis(100))
                .includes(TransientTidalException.class)
                .build());
        this.circuitBreaker = circuitBreakerFactory.create("tidal");
    }

    // ── Endpoints ──────────────────────────────────────────────────────────────────────────

    /** GET /searchResults?filter[query]=…&include=tracks,albums,artists */
    public JsonApiDocument search(String query, Collection<String> include) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("filter[query]", List.of(query));
        params.put("countryCode", List.of(properties.countryCode()));
        params.put("include", List.of(String.join(",", include)));
        return get("/searchResults", params).orElseGet(() -> new JsonApiDocument(null, List.of(), null));
    }

    public Optional<JsonApiDocument> track(String id, Collection<String> include) {
        return get("/tracks/" + encodePath(id), withCountryAndInclude(include));
    }

    public JsonApiDocument tracksByIds(Collection<String> ids, Collection<String> include) {
        Map<String, List<String>> params = withCountryAndInclude(include);
        params.put("filter[id]", List.copyOf(ids));
        return get("/tracks", params).orElseGet(() -> new JsonApiDocument(null, List.of(), null));
    }

    public JsonApiDocument tracksByIsrc(String isrc, Collection<String> include) {
        Map<String, List<String>> params = withCountryAndInclude(include);
        params.put("filter[isrc]", List.of(isrc));
        return get("/tracks", params).orElseGet(() -> new JsonApiDocument(null, List.of(), null));
    }

    public Optional<JsonApiDocument> album(String id, Collection<String> include) {
        return get("/albums/" + encodePath(id), withCountryAndInclude(include));
    }

    public JsonApiDocument albumsByIds(Collection<String> ids, Collection<String> include) {
        Map<String, List<String>> params = withCountryAndInclude(include);
        params.put("filter[id]", List.copyOf(ids));
        return get("/albums", params).orElseGet(() -> new JsonApiDocument(null, List.of(), null));
    }

    public Optional<JsonApiDocument> artist(String id, Collection<String> include) {
        return get("/artists/" + encodePath(id), withCountryAndInclude(include));
    }

    public JsonApiDocument artistsByIds(Collection<String> ids, Collection<String> include) {
        Map<String, List<String>> params = withCountryAndInclude(include);
        params.put("filter[id]", List.copyOf(ids));
        return get("/artists", params).orElseGet(() -> new JsonApiDocument(null, List.of(), null));
    }

    // ── Plumbing ───────────────────────────────────────────────────────────────────────────

    private Map<String, List<String>> withCountryAndInclude(Collection<String> include) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("countryCode", List.of(properties.countryCode()));
        if (include != null && !include.isEmpty()) params.put("include", List.of(String.join(",", include)));
        return params;
    }

    private Optional<JsonApiDocument> get(String path, Map<String, List<String>> params) {
        URI uri = buildUri(path, params);
        try {
            return circuitBreaker.run(() -> {
                try {
                    return retry.execute(() -> doGet(uri));
                } catch (RetryException e) {
                    throw new ProviderUnavailableException(Provider.TIDAL, e.getLastException());
                }
            });
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Circuit open (CallNotPermittedException) or any other breaker-level failure.
            throw new ProviderUnavailableException(Provider.TIDAL, e);
        }
    }

    private Optional<JsonApiDocument> doGet(URI uri) {
        try {
            JsonApiDocument doc = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.accessToken())
                    .retrieve()
                    .body(JsonApiDocument.class);
            return Optional.ofNullable(doc);
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) return Optional.empty();
            if (status == HttpStatus.UNAUTHORIZED) {
                auth.invalidate();
                throw new TransientTidalException("401 from TIDAL, token invalidated", e);
            }
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("TIDAL rate limit hit for {}", uri.getPath());
                throw new TransientTidalException("429 from TIDAL", e);
            }
            throw new TidalApiException("TIDAL rejected request " + uri.getPath() + ": " + e.getStatusCode(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new TransientTidalException("TIDAL transient failure for " + uri.getPath(), e);
        }
    }

    private URI buildUri(String path, Map<String, List<String>> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((name, values) -> values.forEach(value ->
                query.add(encodeQueryParam(name) + "=" + encodeQueryParam(value))));
        return URI.create(properties.apiBaseUrl() + path + (query.length() == 0 ? "" : "?" + query));
    }

    /** Percent-encodes names/values but keeps `,` so JSON:API `include=a,b` lists survive TIDAL's gateway. */
    private static String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String segment) {
        return UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8);
    }

    /** Retryable: rate limits, 5xx, timeouts, expired token. */
    static class TransientTidalException extends RuntimeException {
        TransientTidalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Not retryable: we sent something TIDAL considers invalid. Surfaces as a 502 to our clients. */
    public static class TidalApiException extends RuntimeException {
        TidalApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

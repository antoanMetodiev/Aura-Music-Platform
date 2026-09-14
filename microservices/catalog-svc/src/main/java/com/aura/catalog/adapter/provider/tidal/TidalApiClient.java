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
    private final TidalRequestThrottle throttle;
    private final RetryTemplate retry;
    private final CircuitBreaker circuitBreaker;

    public TidalApiClient(RestClient.Builder builder,
                          TidalAuthClient auth,
                          TidalProperties properties,
                          TidalRequestThrottle throttle,
                          CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, JSON_API)
                .build();
        this.auth = auth;
        this.properties = properties;
        this.throttle = throttle;
        // A 429 pauses the throttle for TIDAL's Retry-After, so the retry itself only needs a
        // small delay of its own — the real wait happens in throttle.acquire().
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(5)
                .delay(Duration.ofMillis(500))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(4))
                .jitter(Duration.ofMillis(200))
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

    /**
     * Follows a JSON:API cursor link exactly as TIDAL handed it to us (already a relative,
     * query-encoded path such as {@code /searchResults/…/relationships/tracks?page[cursor]=…}).
     */
    public JsonApiDocument page(String relativeLink) {
        URI uri = URI.create(properties.apiBaseUrl() + relativeLink);
        return get(uri).orElseGet(() -> new JsonApiDocument(null, List.of(), null));
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

    /**
     * GET /albums/{id}/relationships/items — the album's ordered track/video linkages, each with
     * {@code meta.volumeNumber}/{@code meta.trackNumber}. First page only; follow {@link #page} for the rest.
     */
    public Optional<JsonApiDocument> albumItems(String albumId) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("countryCode", List.of(properties.countryCode()));
        params.put("include", List.of("items"));
        return get("/albums/" + encodePath(albumId) + "/relationships/items", params);
    }

    /**
     * GET /artists/{id}/relationships/tracks — every track the artist appears on (own releases and
     * features). First page only; follow {@link #page} for the rest. {@code collapseBy} is TIDAL's
     * {@code FINGERPRINT} (one entry per distinct recording) or {@code NONE} (every release of it).
     */
    public Optional<JsonApiDocument> artistTracks(String artistId, String collapseBy) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("countryCode", List.of(properties.countryCode()));
        params.put("collapseBy", List.of(collapseBy));
        params.put("include", List.of("tracks"));
        return get("/artists/" + encodePath(artistId) + "/relationships/tracks", params);
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
        return get(buildUri(path, params));
    }

    private Optional<JsonApiDocument> get(URI uri) {
        try {
            return circuitBreaker.run(() -> {
                try {
                    return retry.execute(() -> doGet(uri));
                } catch (RetryException e) {
                    // RetryTemplate wraps even non-retryable failures; a 4xx TIDAL rejected must stay one.
                    Throwable last = e.getLastException();
                    if (last instanceof TidalApiException rejected) throw rejected;
                    log.warn("TIDAL retries exhausted for {}", uri, last);
                    throw new ProviderUnavailableException(Provider.TIDAL, last);
                }
            });
        } catch (ProviderUnavailableException | TidalApiException e) {
            throw e;
        } catch (RuntimeException e) {
            // The circuit breaker wraps whatever the supplier threw (NoFallbackAvailableException);
            // our own classifications must come back out intact.
            Throwable cause = e.getCause();
            if (cause instanceof ProviderUnavailableException || cause instanceof TidalApiException) throw (RuntimeException) cause;
            // Circuit open (CallNotPermittedException) or any other breaker-level failure.
            log.warn("Unexpected failure calling TIDAL for {}", uri, e);
            throw new ProviderUnavailableException(Provider.TIDAL, e);
        }
    }

    private Optional<JsonApiDocument> doGet(URI uri) {
        try {
            throttle.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(Provider.TIDAL, e);
        }
        try {
            return doGetThrottled(uri);
        } finally {
            throttle.release();
        }
    }

    private Optional<JsonApiDocument> doGetThrottled(URI uri) {
        TidalAuthClient.Token token = auth.accessToken();
        try {
            JsonApiDocument doc = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
                    .retrieve()
                    .body(JsonApiDocument.class);
            throttle.succeeded();
            return Optional.ofNullable(doc);
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) return Optional.empty();
            if (status == HttpStatus.UNAUTHORIZED) {
                auth.invalidate(token.credentials());
                throw new TransientTidalException("401 from TIDAL, token invalidated", e);
            }
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                Duration retryAfter = retryAfter(e.getResponseHeaders());
                log.warn("TIDAL rate limit hit for {}, pausing {}", uri.getPath(), retryAfter);
                throttle.rateLimited(retryAfter);
                // With several keys configured the retry goes out on the next one; with one key
                // the token stays valid — re-fetching it would just be another request.
                auth.switchKeyIfPossible(token.credentials(), "429 rate limited");
                throw new TransientTidalException("429 from TIDAL", e);
            }
            throw new TidalApiException("TIDAL rejected request " + uri.getPath() + ": " + e.getStatusCode(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new TransientTidalException("TIDAL transient failure for " + uri.getPath(), e);
        }
    }

    /** TIDAL sends {@code Retry-After} in seconds; fall back to a few seconds when it's missing or malformed. */
    private static Duration retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value != null) {
            try {
                return Duration.ofSeconds(Math.max(1, Long.parseLong(value.trim())));
            } catch (NumberFormatException ignored) {
                // date-formatted Retry-After — not worth parsing, use the default
            }
        }
        return Duration.ofSeconds(4);
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

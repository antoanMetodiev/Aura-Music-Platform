package com.aura.catalog.adapter.provider.lrclib;

import com.aura.catalog.adapter.provider.lrclib.dto.LrclibLyricsResponse;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.service.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * Thin, resilient HTTP layer over the LRCLIB API — same shape as {@code TidalApiClient}: timeouts from
 * {@code spring.http.client.*}, retries on 429/5xx/I-O, circuit breaker outside the retry. 404 is a
 * normal "no lyrics for that", never a failure.
 */
@Component
public class LrclibApiClient {

    private static final Logger log = LoggerFactory.getLogger(LrclibApiClient.class);
    private static final ParameterizedTypeReference<List<LrclibLyricsResponse>> LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final LrclibProperties properties;
    private final RetryTemplate retry;
    private final CircuitBreaker circuitBreaker;

    public LrclibApiClient(RestClient.Builder builder, LrclibProperties properties,
                           CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
        this.properties = properties;
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(3)
                .delay(Duration.ofMillis(400))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(3))
                .jitter(Duration.ofMillis(150))
                .includes(TransientLrclibException.class)
                .build());
        this.circuitBreaker = circuitBreakerFactory.create("lrclib");
    }

    // ── Endpoints ──────────────────────────────────────────────────────────────────────────

    /**
     * GET /get?artist_name=&track_name=[&album_name=]&duration= — exact lookup; LRCLIB itself allows
     * ±2s on duration. Empty on 404.
     */
    public Optional<LrclibLyricsResponse> get(String artistName, String trackName, String albumName, long durationSeconds) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("artist_name", artistName);
        params.put("track_name", trackName);
        if (albumName != null && !albumName.isBlank()) params.put("album_name", albumName);
        params.put("duration", Long.toString(durationSeconds));
        URI uri = buildUri("/get", params);
        return guarded(uri, () -> Optional.ofNullable(restClient.get().uri(uri).retrieve().body(LrclibLyricsResponse.class)));
    }

    /** GET /search?track_name=&artist_name= — fuzzy; the caller filters by duration. */
    public List<LrclibLyricsResponse> search(String artistName, String trackName) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("track_name", trackName);
        params.put("artist_name", artistName);
        URI uri = buildUri("/search", params);
        return guarded(uri, () -> {
            List<LrclibLyricsResponse> body = restClient.get().uri(uri).retrieve().body(LIST);
            return body == null ? List.<LrclibLyricsResponse>of() : body;
        });
    }

    // ── Plumbing ───────────────────────────────────────────────────────────────────────────

    private <T> T guarded(URI uri, Supplier<T> call) {
        try {
            return circuitBreaker.run(() -> {
                try {
                    return retry.execute(() -> classify(uri, call));
                } catch (RetryException e) {
                    // RetryTemplate wraps even non-retryable failures; a 4xx LRCLIB rejected must stay one.
                    Throwable last = e.getLastException();
                    if (last instanceof LrclibApiException rejected) throw rejected;
                    log.warn("LRCLIB retries exhausted for {}", uri.getPath(), last);
                    throw new ProviderUnavailableException(Provider.LRCLIB, last);
                }
            });
        } catch (ProviderUnavailableException | LrclibApiException e) {
            throw e;
        } catch (RuntimeException e) {
            // The circuit breaker wraps whatever the supplier threw; our own classifications must come back out intact.
            Throwable cause = e.getCause();
            if (cause instanceof ProviderUnavailableException || cause instanceof LrclibApiException) throw (RuntimeException) cause;
            log.warn("Unexpected failure calling LRCLIB for {}", uri.getPath(), e);
            throw new ProviderUnavailableException(Provider.LRCLIB, e);
        }
    }

    /**
     * 404 → the "nothing" value of the call ({@code Optional.empty()} / empty list); 429, 5xx and I/O → retry;
     * any other 4xx → we sent something LRCLIB rejects, not retryable.
     */
    @SuppressWarnings("unchecked")
    private <T> T classify(URI uri, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                return uri.getPath().endsWith("/search") ? (T) List.of() : (T) Optional.empty();
            }
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new TransientLrclibException("429 from LRCLIB", e);
            }
            throw new LrclibApiException("LRCLIB rejected request " + uri.getPath() + ": " + e.getStatusCode(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new TransientLrclibException("LRCLIB transient failure for " + uri.getPath(), e);
        }
    }

    private URI buildUri(String path, Map<String, String> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((name, value) -> query.add(encode(name) + "=" + encode(value)));
        return URI.create(properties.baseUrl() + path + (query.length() == 0 ? "" : "?" + query));
    }

    private static String encode(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    /** Retryable: rate limits, 5xx, timeouts. */
    static class TransientLrclibException extends RuntimeException {
        TransientLrclibException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Not retryable: LRCLIB considers the request invalid. Surfaces as a 502 to our clients. */
    public static class LrclibApiException extends RuntimeException {
        LrclibApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

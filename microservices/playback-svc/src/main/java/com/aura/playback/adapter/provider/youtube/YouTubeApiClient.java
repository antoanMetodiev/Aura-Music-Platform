package com.aura.playback.adapter.provider.youtube;

import com.aura.playback.adapter.provider.youtube.dto.YouTubeSearchResponse;
import com.aura.playback.adapter.provider.youtube.dto.YouTubeVideosResponse;
import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.service.PlaybackProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
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
import java.util.StringJoiner;

/**
 * Thin, resilient HTTP layer over the YouTube Data API v3 (Project-Info.md §16, §20). Auth is a
 * plain API key (query param) — no OAuth flow needed for read-only public search/video lookups.
 *
 * Resilience mirrors {@code TidalApiClient}: transient failures (5xx, I/O, 429) retry with
 * exponential backoff; the circuit breaker sits outside the retry. A 403 from YouTube almost always
 * means "quota exceeded" or "API key invalid/restricted" — neither is transient, so it's surfaced
 * immediately rather than retried (retrying a quota-exceeded call only burns more of the quota).
 */
@Component
public class YouTubeApiClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeApiClient.class);

    private final RestClient restClient;
    private final YouTubeProperties properties;
    private final YouTubeApiKeySource keys;
    private final RetryTemplate retry;
    private final CircuitBreaker circuitBreaker;

    public YouTubeApiClient(RestClient.Builder builder, YouTubeProperties properties, YouTubeApiKeySource keys,
                            CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone().baseUrl(properties.apiBaseUrl()).build();
        this.properties = properties;
        this.keys = keys;
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(2)
                .delay(Duration.ofMillis(250))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(2))
                .jitter(Duration.ofMillis(100))
                .includes(TransientYouTubeException.class)
                .build());
        this.circuitBreaker = circuitBreakerFactory.create("youtube");
    }

    /** GET /search?part=snippet&type=video&videoCategoryId=10&q=… */
    public YouTubeSearchResponse search(String query) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("part", List.of("snippet"));
        params.put("type", List.of("video"));
        params.put("videoCategoryId", List.of(properties.musicCategoryId()));
        params.put("maxResults", List.of(String.valueOf(properties.maxSearchResults())));
        params.put("regionCode", List.of(properties.regionCode()));
        params.put("safeSearch", List.of("none"));
        params.put("q", List.of(query));
        return get("/search", params, YouTubeSearchResponse.class)
                .orElseGet(() -> new YouTubeSearchResponse(List.of()));
    }

    /** GET /videos?part=snippet,contentDetails,status&id=id1,id2,… — capped at 50 ids per YouTube's own limit. */
    public YouTubeVideosResponse videosByIds(Collection<String> ids) {
        if (ids.isEmpty()) return new YouTubeVideosResponse(List.of());
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("part", List.of("snippet,contentDetails,status"));
        params.put("id", List.of(String.join(",", ids)));
        return get("/videos", params, YouTubeVideosResponse.class)
                .orElseGet(() -> new YouTubeVideosResponse(List.of()));
    }

    // ── Plumbing ───────────────────────────────────────────────────────────────────────────

    private <T> java.util.Optional<T> get(String path, Map<String, List<String>> params, Class<T> type) {
        URI uriForLogging = buildUri(path, params); // never log the API key
        YouTubeApiKeySource.ApiKey key = keys.current();
        try {
            return getWithKey(path, params, type, key, uriForLogging);
        } catch (QuotaExceededException e) {
            // Daily quota gone on this key — one retry on the next key, if there is one.
            if (!keys.markQuotaExhausted(key)) throw new YouTubeApiException(e.getMessage(), e.getCause());
            YouTubeApiKeySource.ApiKey next = keys.current();
            try {
                return getWithKey(path, params, type, next, uriForLogging);
            } catch (QuotaExceededException again) {
                keys.markQuotaExhausted(next);
                throw new YouTubeApiException(again.getMessage(), again.getCause());
            }
        }
    }

    private <T> java.util.Optional<T> getWithKey(String path, Map<String, List<String>> params, Class<T> type,
                                                 YouTubeApiKeySource.ApiKey key, URI uriForLogging) {
        Map<String, List<String>> withKey = new LinkedHashMap<>(params);
        withKey.put("key", List.of(key.value()));
        URI uri = buildUri(path, withKey);
        try {
            java.util.Optional<T> result = circuitBreaker.run(() -> {
                try {
                    return retry.execute(() -> doGet(uri, uriForLogging, type));
                } catch (RetryException e) {
                    log.warn("YouTube retries exhausted for {}", uriForLogging, e.getLastException());
                    throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, e.getLastException());
                }
            });
            keys.markUsed(key);
            return result;
        } catch (PlaybackProviderUnavailableException | YouTubeApiException | QuotaExceededException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Unexpected failure calling YouTube for {}", uriForLogging, e);
            throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, e);
        }
    }

    private <T> java.util.Optional<T> doGet(URI uri, URI uriForLogging, Class<T> type) {
        try {
            T body = restClient.get().uri(uri).retrieve().body(type);
            return java.util.Optional.ofNullable(body);
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.FORBIDDEN) {
                String body = e.getResponseBodyAsString();
                if (body != null && (body.contains("quotaExceeded") || body.contains("dailyLimitExceeded"))) {
                    throw new QuotaExceededException("YouTube daily quota exhausted for " + uriForLogging.getPath(), e);
                }
                throw new YouTubeApiException(
                        "YouTube rejected request " + uriForLogging.getPath()
                                + " with 403 — check API key restrictions and remaining quota", e);
            }
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("YouTube rate limit hit for {}", uriForLogging.getPath());
                throw new TransientYouTubeException("429 from YouTube", e);
            }
            throw new YouTubeApiException("YouTube rejected request " + uriForLogging.getPath() + ": " + e.getStatusCode(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new TransientYouTubeException("YouTube transient failure for " + uriForLogging.getPath(), e);
        }
    }

    private URI buildUri(String path, Map<String, List<String>> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((name, values) -> values.forEach(value ->
                query.add(encodeQueryParam(name) + "=" + encodeQueryParam(value))));
        return URI.create(properties.apiBaseUrl() + path + (query.length() == 0 ? "" : "?" + query));
    }

    private static String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    /** Retryable: rate limits, 5xx, timeouts. */
    static class TransientYouTubeException extends RuntimeException {
        TransientYouTubeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The key's daily quota is spent — handled by rotating keys in {@link #get}, never retried on the same key. */
    static class QuotaExceededException extends RuntimeException {
        QuotaExceededException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Not retryable: bad request, invalid/restricted key, or quota exceeded. Surfaces as a 502 to our clients. */
    public static class YouTubeApiException extends RuntimeException {
        YouTubeApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

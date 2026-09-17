package com.aura.recommendation.adapter.provider.lastfm;

import com.aura.recommendation.adapter.provider.lastfm.dto.LastFmSimilarResponse;
import com.aura.recommendation.adapter.provider.lastfm.dto.LastFmTopTagsResponse;
import com.aura.recommendation.domain.model.Provider;
import com.aura.recommendation.domain.port.ArtistSimilarityProvider;
import com.aura.recommendation.domain.port.ProviderSimilarArtist;
import com.aura.recommendation.domain.port.ProviderTag;
import com.aura.recommendation.domain.service.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
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
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * {@link ArtistSimilarityProvider} over Last.fm's {@code artist.getSimilar} and {@code artist.getTopTags}.
 *
 * <p>Two failure modes are carefully kept apart, because confusing them corrupts the graph
 * permanently: <em>error 6 (artist not found)</em> is a real answer — they have no neighbours for this
 * artist, record it and don't ask again until the refresh interval — while a 5xx, a timeout, a 429 or
 * an open circuit is an <em>outage</em>, which must propagate so the worker retries the artist later
 * instead of writing down "no neighbours" forever.
 */
@Component
public class LastFmSimilarityProvider implements ArtistSimilarityProvider {

    private static final Logger log = LoggerFactory.getLogger(LastFmSimilarityProvider.class);
    private static final int ERROR_NOT_FOUND = 6;
    private static final int ERROR_RATE_LIMIT = 29;
    private static final int MAX_TAGS = 10;
    /** Tags below this weight are noise ("seen live", or one listener's private label). */
    private static final int MIN_TAG_WEIGHT = 10;
    private static final Duration RATE_LIMIT_PAUSE = Duration.ofSeconds(20);

    private final RestClient restClient;
    private final LastFmProperties properties;
    private final LastFmRequestThrottle throttle;
    private final CircuitBreaker circuitBreaker;

    public LastFmSimilarityProvider(RestClient.Builder builder, LastFmProperties properties,
                                    LastFmRequestThrottle throttle, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone()
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.properties = properties;
        this.throttle = throttle;
        this.circuitBreaker = circuitBreakerFactory.create("lastfm");
        if (!properties.configured()) {
            log.warn("Last.fm API key not configured — the taste graph cannot be built; set LASTFM_API_KEY");
        }
    }

    @Override
    public Provider provider() {
        return Provider.LASTFM;
    }

    @Override
    public List<ProviderSimilarArtist> similarTo(String artistName, int limit) {
        if (!properties.configured() || artistName == null || artistName.isBlank()) return List.of();
        URI uri = uri("artist.getsimilar", artistName, "&limit=" + Math.max(1, limit));
        LastFmSimilarResponse body = call(uri, LastFmSimilarResponse.class, LastFmSimilarResponse::error, LastFmSimilarResponse::message);
        if (body == null || body.similarartists() == null || body.similarartists().artist() == null) return List.of();
        return body.similarartists().artist().stream()
                .filter(a -> a.name() != null && !a.name().isBlank())
                .map(a -> new ProviderSimilarArtist(a.name().trim(), parseMatch(a.match()), blankToNull(a.mbid()), a.url()))
                .filter(a -> a.match() > 0)
                .toList();
    }

    @Override
    public List<ProviderTag> topTags(String artistName) {
        if (!properties.configured() || artistName == null || artistName.isBlank()) return List.of();
        URI uri = uri("artist.gettoptags", artistName, "");
        LastFmTopTagsResponse body = call(uri, LastFmTopTagsResponse.class, LastFmTopTagsResponse::error, LastFmTopTagsResponse::message);
        if (body == null || body.toptags() == null || body.toptags().tag() == null) return List.of();
        return body.toptags().tag().stream()
                .filter(t -> t.name() != null && !t.name().isBlank())
                .map(t -> new ProviderTag(t.name().trim().toLowerCase(Locale.ROOT), t.count() == null ? 0 : t.count()))
                .filter(t -> t.count() >= MIN_TAG_WEIGHT)
                .limit(MAX_TAGS)
                .toList();
    }

    // ── Plumbing ───────────────────────────────────────────────────────────────────────────

    private URI uri(String method, String artistName, String extra) {
        return URI.create(properties.apiBaseUrl()
                + "?method=" + method
                + "&format=json&autocorrect=1"
                + "&artist=" + UriUtils.encodeQueryParam(artistName, StandardCharsets.UTF_8)
                + extra
                + "&api_key=" + properties.apiKey());
    }

    /** @return the body, or {@code null} when the provider simply doesn't know this artist (error 6 / 404). */
    private <T> T call(URI uri, Class<T> type, Function<T, Integer> error, Function<T, String> message) {
        try {
            return circuitBreaker.run(() -> fetch(uri, type, error, message));
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ProviderUnavailableException unavailable) throw unavailable;
            // Circuit open, or any other breaker-level failure — an outage, never an answer.
            log.warn("Unexpected failure calling Last.fm {}", scrub(uri), e);
            throw new ProviderUnavailableException(Provider.LASTFM, e);
        }
    }

    private <T> T fetch(URI uri, Class<T> type, Function<T, Integer> error, Function<T, String> message) {
        try {
            throttle.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(Provider.LASTFM, e);
        }
        try {
            T body = restClient.get().uri(uri).retrieve().body(type);
            if (body == null) return null;
            Integer code = error.apply(body);
            if (code != null) {
                if (code == ERROR_NOT_FOUND) return null;
                if (code == ERROR_RATE_LIMIT) throttle.rateLimited(RATE_LIMIT_PAUSE);
                throw new ProviderUnavailableException(Provider.LASTFM,
                        new IllegalStateException("Last.fm error " + code + ": " + message.apply(body)));
            }
            throttle.succeeded();
            return body;
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) return null;
            if (status == HttpStatus.TOO_MANY_REQUESTS) throttle.rateLimited(RATE_LIMIT_PAUSE);
            throw new ProviderUnavailableException(Provider.LASTFM, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ProviderUnavailableException(Provider.LASTFM, e);
        }
    }

    private static double parseMatch(String value) {
        try {
            return value == null ? 0 : Math.clamp(Double.parseDouble(value.trim()), 0.0, 1.0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Never log the API key. */
    private static String scrub(URI uri) {
        return uri.toString().replaceAll("api_key=[^&]*", "api_key=***");
    }
}

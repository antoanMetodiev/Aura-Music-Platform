package com.aura.playback.adapter.provider.musicbrainz;

import com.aura.playback.domain.model.CanonicalTrack;
import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.port.KnownVideoLookup;
import com.aura.playback.domain.port.VideoHintStore.HintSource;
import org.springframework.core.annotation.Order;
import com.aura.playback.domain.service.PlaybackProviderUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link KnownVideoLookup} over MusicBrainz's ISRC endpoint: {@code GET /isrc/{isrc}?inc=url-rels}
 * returns the recordings carrying that ISRC together with their URL relationships; a
 * {@code youtube.com} / {@code music.youtube.com} / {@code youtu.be} link among them is the answer.
 *
 * <p>Paced to one call per second (their published limit) and retried a few times when they answer
 * "server busy", which happens routinely under load. Everything else that fails is "unavailable".
 */
/* First in the chain: keyed by ISRC, so exact by construction — Discogs (by name) is only asked when this has nothing. */
@Order(1)
@Component
@ConditionalOnProperty(prefix = "music.providers.musicbrainz", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MusicBrainzVideoLookup implements KnownVideoLookup {

    private static final Logger log = LoggerFactory.getLogger(MusicBrainzVideoLookup.class);
    private static final Pattern YOUTUBE_ID = Pattern.compile(
            "(?:youtube\\.com/watch\\?(?:.*&)?v=|youtu\\.be/|youtube\\.com/(?:embed|v|shorts)/)([A-Za-z0-9_-]{11})");
    private static final int BUSY_RETRIES = 4;

    private final RestClient restClient;
    private final MusicBrainzProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Object pacing = new Object();
    private long nextSlotNanos = System.nanoTime();

    public MusicBrainzVideoLookup(RestClient.Builder builder, MusicBrainzProperties properties,
                                  CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.properties = properties;
        this.circuitBreaker = circuitBreakerFactory.create("musicbrainz");
    }

    @Override
    public HintSource source() {
        return HintSource.MUSICBRAINZ;
    }

    @Override
    public boolean supports(CanonicalTrack track) {
        return track.isrc() != null && !track.isrc().isBlank();
    }

    @Override
    public Result lookup(CanonicalTrack track) {
        return lookupByIsrc(track.isrc());
    }

    Result lookupByIsrc(String isrc) {
        URI uri = URI.create(properties.apiBaseUrl() + "/isrc/" + UriUtils.encodePathSegment(isrc, StandardCharsets.UTF_8)
                + "?inc=url-rels&fmt=json");
        try {
            return circuitBreaker.run(() -> fetchWithBusyRetry(uri));
        } catch (PlaybackProviderUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PlaybackProviderUnavailableException unavailable) throw unavailable;
            throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, e);
        }
    }

    private Result fetchWithBusyRetry(URI uri) {
        for (int attempt = 1; ; attempt++) {
            pace();
            try {
                IsrcResponse body = restClient.get().uri(uri).retrieve().body(IsrcResponse.class);
                if (body == null) return Result.notFound();
                if (body.error() != null) {
                    if (body.error().contains("busy") && attempt <= BUSY_RETRIES) continue;
                    throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, new IllegalStateException(body.error()));
                }
                return toResult(body);
            } catch (HttpClientErrorException e) {
                if (HttpStatus.resolve(e.getStatusCode().value()) == HttpStatus.NOT_FOUND) return Result.notFound();
                throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, e);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                if (attempt <= BUSY_RETRIES) continue; // 503 is their "busy" too
                throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, e);
            }
        }
    }

    private static Result toResult(IsrcResponse body) {
        List<Recording> recordings = body.recordings() == null ? List.of() : body.recordings();
        if (recordings.isEmpty()) return Result.notFound();
        // Prefer a plain youtube.com link over music.youtube.com (same ids, but the former is more
        // often the official video); any hit wins over none.
        Optional<String> best = Optional.empty();
        for (Recording r : recordings) {
            for (Relation rel : r.relations() == null ? List.<Relation>of() : r.relations()) {
                String href = rel.url() == null ? null : rel.url().resource();
                if (href == null) continue;
                Matcher m = YOUTUBE_ID.matcher(href);
                if (!m.find()) continue;
                if (best.isEmpty() || href.contains("www.youtube.com")) best = Optional.of(m.group(1));
            }
        }
        if (best.isPresent()) {
            log.debug("MusicBrainz: ISRC {} -> YouTube {}", body.recordings().getFirst().id(), best.get());
            return Result.found(best.get());
        }
        return Result.noLink();
    }

    /** One call per {@code minRequestInterval}, process-wide. */
    private void pace() {
        long waitNanos;
        synchronized (pacing) {
            long now = System.nanoTime();
            long slot = Math.max(nextSlotNanos, now);
            nextSlotNanos = slot + properties.minRequestInterval().toNanos();
            waitNanos = slot - now;
        }
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, e);
            }
        }
    }

    // ── Wire shapes (only what we read) ────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IsrcResponse(String isrc, List<Recording> recordings, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Recording(String id, String title, Long length, List<Relation> relations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Relation(String type, @JsonProperty("target-type") String targetType, Url url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Url(String resource) {
    }
}

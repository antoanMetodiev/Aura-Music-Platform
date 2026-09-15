package com.aura.playback.adapter.provider.discogs;

import com.aura.playback.domain.model.CanonicalTrack;
import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.port.KnownVideoLookup;
import com.aura.playback.domain.port.VideoHintStore.HintSource;
import com.aura.playback.domain.service.PlaybackProviderUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.annotation.Order;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link KnownVideoLookup} over Discogs. Discogs has no ISRC lookup, so this goes by name:
 * {@code /database/search?artist=&track=} finds the release (master first — it aggregates every
 * edition's videos — then a plain release), and the release page's {@code videos[]} are the
 * candidates. Those are per <em>release</em>, not per track, so the whole list comes back with
 * titles and durations and the service ranks them against the track before spending a YouTube unit.
 *
 * <p>Paced to one call per {@code minRequestInterval} (their limit is 60/min with a token); a 429 or
 * 5xx is "unavailable" and the worker backs off. Two calls per track, three when a master search
 * is empty and a release search is tried.
 */
@Order(2)
@Component
@ConditionalOnProperty(prefix = "music.providers.discogs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DiscogsVideoLookup implements KnownVideoLookup {

    private static final Logger log = LoggerFactory.getLogger(DiscogsVideoLookup.class);
    private static final Pattern YOUTUBE_ID = Pattern.compile(
            "(?:youtube\\.com/watch\\?(?:.*&)?v=|youtu\\.be/|youtube\\.com/(?:embed|v|shorts)/)([A-Za-z0-9_-]{11})");

    private final RestClient restClient;
    private final DiscogsProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Object pacing = new Object();
    private long nextSlotNanos = System.nanoTime();

    public DiscogsVideoLookup(RestClient.Builder builder, DiscogsProperties properties,
                              CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        RestClient.Builder b = builder.clone()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (properties.authenticated()) {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Discogs token=" + properties.token());
        } else {
            log.warn("Discogs token not configured — running unauthenticated at 25 requests/minute; set DISCOGS_TOKEN");
        }
        this.restClient = b.build();
        this.properties = properties;
        this.circuitBreaker = circuitBreakerFactory.create("discogs");
    }

    @Override
    public HintSource source() {
        return HintSource.DISCOGS;
    }

    /** Needs a name to search by — nothing else. */
    @Override
    public boolean supports(CanonicalTrack track) {
        return track.title() != null && !track.title().isBlank() && !track.primaryArtist().isBlank();
    }

    @Override
    public Result lookup(CanonicalTrack track) {
        try {
            return circuitBreaker.run(() -> doLookup(track));
        } catch (PlaybackProviderUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PlaybackProviderUnavailableException unavailable) throw unavailable;
            throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, e);
        }
    }

    private Result doLookup(CanonicalTrack track) {
        Optional<SearchResult> hit = search(track, "master");
        if (hit.isEmpty()) hit = search(track, "release");
        if (hit.isEmpty()) return Result.notFound();

        SearchResult r = hit.get();
        boolean useMaster = r.masterId() != null && r.masterId() > 0;
        String path = useMaster ? "/masters/" + r.masterId() : "/releases/" + r.id();
        Optional<ReleasePage> page = get(path, ReleasePage.class);
        if (page.isEmpty()) return Result.notFound();

        List<KnownVideo> videos = new ArrayList<>();
        for (Video v : page.get().videos() == null ? List.<Video>of() : page.get().videos()) {
            if (v.uri() == null || Boolean.FALSE.equals(v.embed())) continue;
            Matcher m = YOUTUBE_ID.matcher(v.uri());
            if (!m.find()) continue;
            long durationMs = v.duration() == null ? 0L : v.duration() * 1000L;
            videos.add(new KnownVideo(m.group(1), v.title(), durationMs));
        }
        if (videos.isEmpty()) return Result.noLink();
        log.debug("Discogs: '{} - {}' -> {} {} with {} YouTube videos", track.primaryArtist(), track.title(),
                useMaster ? "master" : "release", useMaster ? r.masterId() : r.id(), videos.size());
        return Result.found(videos);
    }

    /**
     * First result whose "Artist - Title" starts with our artist, else the first result at all —
     * Discogs' own ranking is decent, the artist check just guards against a same-titled song by
     * somebody else outranking a sparse page.
     */
    private Optional<SearchResult> search(CanonicalTrack track, String type) {
        String query = "/database/search?type=" + type
                + "&artist=" + encode(track.primaryArtist())
                + "&track=" + encode(track.title())
                + "&per_page=" + properties.searchResults();
        Optional<SearchResponse> response = get(query, SearchResponse.class);
        List<SearchResult> results = response.map(SearchResponse::results).orElse(List.of());
        if (results == null || results.isEmpty()) return Optional.empty();
        String artist = normalize(track.primaryArtist());
        return results.stream()
                .filter(r -> r.title() != null && normalize(r.title()).startsWith(artist))
                .findFirst()
                .or(() -> Optional.of(results.getFirst()));
    }

    private <T> Optional<T> get(String pathAndQuery, Class<T> type) {
        pace();
        URI uri = URI.create(properties.apiBaseUrl() + pathAndQuery);
        try {
            return Optional.ofNullable(restClient.get().uri(uri).retrieve().body(type));
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) return Optional.empty();
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Discogs rate limit hit ({} remaining) — backing off", e.getResponseHeaders() == null ? "?"
                        : e.getResponseHeaders().getFirst("x-discogs-ratelimit-remaining"));
            }
            throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new PlaybackProviderUnavailableException(PlaybackProvider.YOUTUBE, e);
        }
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

    private static String encode(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    // ── Wire shapes (only what we read) ────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<SearchResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResult(long id, @JsonProperty("master_id") Long masterId, String title, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReleasePage(long id, String title, List<Video> videos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Video(String uri, String title, Integer duration, Boolean embed) {
    }
}

package com.aura.catalog.adapter.provider.discogs;

import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.port.ArtistInfoProvider;
import com.aura.catalog.domain.port.ProviderArtistInfo;
import com.aura.catalog.domain.service.ProviderUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@link ArtistInfoProvider} over Discogs: {@code /database/search?type=artist} then
 * {@code /artists/{id}} for the artist's outside links (official site, socials, Wikipedia — the
 * community keeps these current) and its short profile, used as the biography only when Last.fm
 * has none (Bulgarian artists, mostly). Second in the chain; two paced calls per artist.
 */
@Order(2)
@Component
@ConditionalOnProperty(prefix = "music.providers.discogs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DiscogsArtistInfoProvider implements ArtistInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(DiscogsArtistInfoProvider.class);
    /** Discogs profile markup: {@code [a123]} / {@code [l456]} entity refs and {@code [url=...]text[/url]}. */
    private static final Pattern ENTITY_REF = Pattern.compile("\\[[alrm]\\d+]");
    private static final Pattern URL_TAG = Pattern.compile("\\[url=[^]]*]([^\\[]*)\\[/url]");
    private static final Pattern BB_TAG = Pattern.compile("\\[/?[a-z]+]");

    private final RestClient restClient;
    private final DiscogsProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Object pacing = new Object();
    private long nextSlotNanos = System.nanoTime();

    public DiscogsArtistInfoProvider(RestClient.Builder builder, DiscogsProperties properties,
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
    public Provider provider() {
        return Provider.DISCOGS;
    }

    @Override
    public Optional<ProviderArtistInfo> find(Artist artist, String language) {
        try {
            return circuitBreaker.run(() -> doFind(artist));
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ProviderUnavailableException unavailable) throw unavailable;
            log.warn("Unexpected failure calling Discogs for '{}'", artist.name(), e);
            throw new ProviderUnavailableException(Provider.DISCOGS, e);
        }
    }

    private Optional<ProviderArtistInfo> doFind(Artist artist) {
        String wanted = normalize(artist.name());
        Optional<SearchResponse> search = get("/database/search?type=artist&per_page=5&q="
                + UriUtils.encodeQueryParam(artist.name(), StandardCharsets.UTF_8), SearchResponse.class);
        List<SearchResult> results = search.map(SearchResponse::results).orElse(List.of());
        // Name search is fuzzy; only an exact (normalized) name match is trusted — a wrong artist's
        // Instagram on the page is worse than none.
        Optional<SearchResult> hit = results.stream()
                .filter(r -> r.title() != null && normalize(r.title()).equals(wanted))
                .findFirst();
        if (hit.isEmpty()) return Optional.empty();

        Optional<ArtistPage> page = get("/artists/" + hit.get().id(), ArtistPage.class);
        if (page.isEmpty()) return Optional.empty();
        ArtistPage a = page.get();
        String profile = cleanProfile(a.profile());
        List<String> urls = a.urls() == null ? List.of() : a.urls().stream().filter(u -> u != null && !u.isBlank()).toList();
        return Optional.of(new ProviderArtistInfo(
                profile,
                profile == null ? null : "https://www.discogs.com/artist/" + a.id(),
                profile == null ? null : "en",
                null, null,
                List.of(), List.of(),
                urls
        ));
    }

    /**
     * Discogs profiles are wiki-ish markup; keep the plain sentences. Lines that reference other Discogs
     * entities ({@code Daughter of [a6770046] and [a16984243]}) can't be rendered without the names, so
     * they are dropped rather than left with holes.
     */
    static String cleanProfile(String profile) {
        if (profile == null) return null;
        String text = URL_TAG.matcher(profile).replaceAll("$1");
        text = java.util.Arrays.stream(text.split("\\R"))
                .filter(line -> !ENTITY_REF.matcher(line).find())
                .collect(java.util.stream.Collectors.joining("\n"));
        text = BB_TAG.matcher(text).replaceAll("");
        text = text.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
        return text.isEmpty() ? null : text;
    }

    private <T> Optional<T> get(String pathAndQuery, Class<T> type) {
        pace();
        URI uri = URI.create(properties.apiBaseUrl() + pathAndQuery);
        try {
            return Optional.ofNullable(restClient.get().uri(uri).retrieve().body(type));
        } catch (HttpClientErrorException e) {
            if (HttpStatus.resolve(e.getStatusCode().value()) == HttpStatus.NOT_FOUND) return Optional.empty();
            throw new ProviderUnavailableException(Provider.DISCOGS, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ProviderUnavailableException(Provider.DISCOGS, e);
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
                throw new ProviderUnavailableException(Provider.DISCOGS, e);
            }
        }
    }

    /** Discogs disambiguates duplicates as "Name (2)"; that suffix is not part of the name. */
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s*\\(\\d+\\)$", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    // ── Wire shapes (only what we read) ────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<SearchResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResult(long id, String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArtistPage(long id, String name, String profile, List<String> urls) {
    }
}

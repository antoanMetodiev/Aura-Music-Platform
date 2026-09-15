package com.aura.catalog.adapter.provider.lastfm;

import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.ArtistAbout.SimilarArtist;
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
 * {@link ArtistInfoProvider} over Last.fm's {@code artist.getInfo}: biography (in the requested
 * language when their wiki has it, English otherwise), community tags, "similar artists" and
 * listener/playcount stats. First in the chain — it has the text; Discogs fills in the links.
 *
 * <p>The biography comes back with an appended "Read more on Last.fm" anchor and a licence line;
 * both are stripped here and replaced by the structured {@code biographyUrl} the UI credits.
 */
@Order(1)
@Component
@ConditionalOnProperty(prefix = "music.providers.lastfm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LastFmArtistInfoProvider implements ArtistInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(LastFmArtistInfoProvider.class);
    private static final int ERROR_NOT_FOUND = 6;
    private static final int ERROR_RATE_LIMIT = 29;
    private static final int MAX_TAGS = 8;
    private static final int MAX_SIMILAR = 8;
    /** Trailing "<a href="...">Read more on Last.fm</a>. User-contributed text is available under ..." */
    private static final Pattern TRAILER = Pattern.compile("\\s*<a\\s+href=\"[^\"]*\"[^>]*>Read more on Last\\.fm</a>\\.?.*$", Pattern.DOTALL);
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    /** Last.fm's "no biography" for artists nobody has written about yet — a few words of boilerplate, sometimes empty. */
    private static final Pattern EMPTY_BIO = Pattern.compile("^\\s*(this is a placeholder|there (is|are) no (biography|bio|information))", Pattern.CASE_INSENSITIVE);

    private final RestClient restClient;
    private final LastFmProperties properties;
    private final CircuitBreaker circuitBreaker;

    public LastFmArtistInfoProvider(RestClient.Builder builder, LastFmProperties properties,
                                    CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone()
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.properties = properties;
        this.circuitBreaker = circuitBreakerFactory.create("lastfm");
        if (!properties.configured()) {
            log.warn("Last.fm API key not configured — artist biographies/tags/similar artists will be unavailable; set LASTFM_API_KEY");
        }
    }

    @Override
    public Provider provider() {
        return Provider.LASTFM;
    }

    @Override
    public Optional<ProviderArtistInfo> find(Artist artist, String language) {
        if (!properties.configured()) return Optional.empty();
        String lang = language == null || language.isBlank() ? "en" : language.toLowerCase(Locale.ROOT);
        Optional<ArtistResponse> response = getInfo(artist.name(), lang);
        // Their localized wikis are thin; an English entry is better than none.
        if (response.isPresent() && !"en".equals(lang) && !hasText(response.get())) {
            Optional<ArtistResponse> english = getInfo(artist.name(), "en");
            if (english.isPresent() && hasText(english.get())) {
                response = english;
                lang = "en";
            }
        }
        if (response.isEmpty()) return Optional.empty();
        return Optional.of(toInfo(response.get().artist(), lang));
    }

    private Optional<ArtistResponse> getInfo(String name, String lang) {
        URI uri = URI.create(properties.apiBaseUrl()
                + "?method=artist.getinfo&format=json&autocorrect=1"
                + "&artist=" + UriUtils.encodeQueryParam(name, StandardCharsets.UTF_8)
                + "&lang=" + lang
                + "&api_key=" + properties.apiKey());
        try {
            return circuitBreaker.run(() -> call(uri));
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ProviderUnavailableException unavailable) throw unavailable;
            log.warn("Unexpected failure calling Last.fm for '{}'", name, e);
            throw new ProviderUnavailableException(Provider.LASTFM, e);
        }
    }

    /** Last.fm answers errors as 200 with {@code {error, message}} as often as with a 4xx — both are handled. */
    private Optional<ArtistResponse> call(URI uri) {
        try {
            ArtistResponse body = restClient.get().uri(uri).retrieve().body(ArtistResponse.class);
            if (body == null) return Optional.empty();
            if (body.error() != null) {
                if (body.error() == ERROR_NOT_FOUND) return Optional.empty();
                throw new ProviderUnavailableException(Provider.LASTFM,
                        new IllegalStateException("Last.fm error " + body.error() + ": " + body.message()));
            }
            return body.artist() == null ? Optional.empty() : Optional.of(body);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw new ProviderUnavailableException(Provider.LASTFM, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ProviderUnavailableException(Provider.LASTFM, e);
        }
    }

    private static boolean hasText(ArtistResponse r) {
        return r.artist() != null && r.artist().bio() != null && cleanBiography(r.artist().bio().content()) != null;
    }

    private static ProviderArtistInfo toInfo(ArtistDto a, String lang) {
        String bio = a.bio() == null ? null : cleanBiography(a.bio().content());
        List<String> tags = a.tags() == null || a.tags().tag() == null ? List.of()
                : a.tags().tag().stream().map(TagDto::name).filter(n -> n != null && !n.isBlank()).limit(MAX_TAGS).toList();
        List<SimilarArtist> similar = a.similar() == null || a.similar().artist() == null ? List.of()
                : a.similar().artist().stream().filter(s -> s.name() != null && !s.name().isBlank())
                        .map(s -> new SimilarArtist(s.name(), s.url(), null)).limit(MAX_SIMILAR).toList();
        return new ProviderArtistInfo(
                bio,
                bio == null ? null : a.url(),
                bio == null ? null : lang,
                a.stats() == null ? null : parseLong(a.stats().listeners()),
                a.stats() == null ? null : parseLong(a.stats().playcount()),
                tags,
                similar,
                List.of()
        );
    }

    /** Strips the trailing "Read more" anchor + licence line and any other markup; null when nothing real is left. */
    static String cleanBiography(String content) {
        if (content == null) return null;
        String text = TRAILER.matcher(content).replaceFirst("");
        text = TAGS.matcher(text).replaceAll("").strip();
        if (text.isEmpty() || EMPTY_BIO.matcher(text).find()) return null;
        return text;
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Wire shapes (only what we read) ────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArtistResponse(ArtistDto artist, Integer error, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArtistDto(String name, String mbid, String url, StatsDto stats, SimilarDto similar, TagsDto tags, BioDto bio) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatsDto(String listeners, String playcount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SimilarDto(List<SimilarArtistDto> artist) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SimilarArtistDto(String name, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagsDto(List<TagDto> tag) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagDto(String name, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BioDto(String published, String summary, String content) {
    }
}

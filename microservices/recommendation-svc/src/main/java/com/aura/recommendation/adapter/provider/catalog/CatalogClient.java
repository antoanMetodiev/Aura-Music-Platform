package com.aura.recommendation.adapter.provider.catalog;

import com.aura.recommendation.adapter.provider.catalog.dto.CatalogArtistResponse;
import com.aura.recommendation.adapter.provider.catalog.dto.CatalogTrackResponse;
import com.aura.recommendation.domain.model.AlbumRef;
import com.aura.recommendation.domain.model.ArtistRef;
import com.aura.recommendation.domain.model.Artwork;
import com.aura.recommendation.domain.model.TrackRef;
import com.aura.recommendation.domain.port.CatalogLookup;
import com.aura.recommendation.domain.service.CatalogUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link CatalogLookup} over catalog-svc's public API. Every artist and track this service returns
 * comes from here — we own the edges, catalog owns the music (Project-Info.md §6).
 */
@Component
public class CatalogClient implements CatalogLookup {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    private static final ParameterizedTypeReference<List<CatalogTrackResponse>> TRACK_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<CatalogArtistResponse>> ARTIST_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CatalogClient(RestClient.Builder builder, CatalogClientProperties properties,
                         CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone().baseUrl(properties.baseUrl()).build();
        this.circuitBreaker = circuitBreakerFactory.create("catalog");
    }

    @Override
    public Optional<ArtistRef> findArtist(UUID artistId) {
        return guarded("artist " + artistId, () -> {
            try {
                return Optional.ofNullable(restClient.get()
                                .uri("/api/v1/catalog/artists/{id}", artistId)
                                .retrieve()
                                .body(CatalogArtistResponse.class))
                        .map(CatalogClient::toArtist);
            } catch (HttpClientErrorException e) {
                if (notFound(e)) return Optional.empty();
                throw e;
            }
        });
    }

    @Override
    public Optional<TrackRef> findTrack(UUID trackId) {
        return guarded("track " + trackId, () -> {
            try {
                return Optional.ofNullable(restClient.get()
                                .uri("/api/v1/catalog/tracks/{id}", trackId)
                                .retrieve()
                                .body(CatalogTrackResponse.class))
                        .map(CatalogClient::toTrack);
            } catch (HttpClientErrorException e) {
                if (notFound(e)) return Optional.empty();
                throw e;
            }
        });
    }

    @Override
    public List<ArtistRef> findArtistsByIds(Collection<UUID> artistIds) {
        if (artistIds.isEmpty()) return List.of();
        // catalog-svc has no batch-by-id endpoint; by-name is the batch we have, and single reads are
        // local (no provider call) so fanning them out over virtual threads is the caller's business.
        return artistIds.stream().map(this::findArtist).flatMap(Optional::stream).toList();
    }

    @Override
    public List<ArtistRef> findArtistsByNames(Collection<String> names) {
        if (names.isEmpty()) return List.of();
        List<String> distinct = names.stream().filter(n -> n != null && !n.isBlank()).map(String::trim).distinct().toList();
        if (distinct.isEmpty()) return List.of();
        return guarded("artists by name (" + distinct.size() + ")", () -> {
            List<CatalogArtistResponse> body = restClient.get()
                    .uri(b -> b.path("/api/v1/catalog/artists/by-name").queryParam("name", distinct).build())
                    .retrieve()
                    .body(ARTIST_LIST);
            return body == null ? List.<ArtistRef>of() : body.stream().map(CatalogClient::toArtist).toList();
        });
    }

    @Override
    public List<TrackRef> topTracks(UUID artistId, int limit) {
        return guarded("top tracks of " + artistId, () -> {
            try {
                List<CatalogTrackResponse> body = restClient.get()
                        .uri(b -> b.path("/api/v1/catalog/artists/{id}/top-tracks")
                                .queryParam("limit", limit)
                                // Never let a recommendation request trigger a TIDAL discography pull.
                                .queryParam("source", "local")
                                .build(artistId))
                        .retrieve()
                        .body(TRACK_LIST);
                return body == null ? List.<TrackRef>of() : body.stream().map(CatalogClient::toTrack).toList();
            } catch (HttpClientErrorException e) {
                if (notFound(e)) return List.<TrackRef>of();
                throw e;
            }
        });
    }

    @Override
    public List<ArtistRef> scanArtists(double popularityBelow, UUID afterId, int limit) {
        return guarded("artist scan", () -> {
            List<CatalogArtistResponse> body = restClient.get()
                    .uri(b -> b.path("/api/v1/catalog/artists/scan")
                            .queryParam("popularityBelow", popularityBelow)
                            .queryParam("afterId", afterId.toString())
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(ARTIST_LIST);
            return body == null ? List.<ArtistRef>of() : body.stream().map(CatalogClient::toArtist).toList();
        });
    }

    @Override
    public List<TrackRef> scanTracksByPopularity(double popularityBelow, UUID afterId, int limit) {
        return guarded("track scan", () -> {
            List<CatalogTrackResponse> body = restClient.get()
                    .uri(b -> b.path("/api/v1/catalog/tracks/scan")
                            .queryParam("order", "popularity")
                            .queryParam("popularityBelow", popularityBelow)
                            .queryParam("afterId", afterId.toString())
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(TRACK_LIST);
            return body == null ? List.<TrackRef>of() : body.stream().map(CatalogClient::toTrack).toList();
        });
    }

    private <T> T guarded(String what, java.util.function.Supplier<T> call) {
        try {
            return circuitBreaker.run(call::get);
        } catch (RuntimeException e) {
            log.warn("Unable to reach catalog-svc for {}", what, e);
            throw new CatalogUnavailableException(e);
        }
    }

    private static boolean notFound(HttpClientErrorException e) {
        return HttpStatus.resolve(e.getStatusCode().value()) == HttpStatus.NOT_FOUND;
    }

    // ── Mapping ────────────────────────────────────────────────────────────────────────────

    private static ArtistRef toArtist(CatalogArtistResponse r) {
        return new ArtistRef(r.id(), r.name(), toArtwork(r.artwork()), or(r.popularity(), 0.0));
    }

    private static TrackRef toTrack(CatalogTrackResponse r) {
        List<ArtistRef> artists = r.artists() == null ? List.of() : r.artists().stream().map(CatalogClient::toArtist).toList();
        AlbumRef album = r.album() == null ? null
                : new AlbumRef(r.album().id(), r.album().title(),
                r.album().artist() == null ? null : toArtist(r.album().artist()),
                toArtwork(r.album().artwork()), r.album().releaseYear());
        return new TrackRef(r.id(), r.title(), r.version(), or(r.durationMs(), 0L), r.isrc(),
                Boolean.TRUE.equals(r.explicit()), artists, album, toArtwork(r.artwork()), or(r.popularity(), 0.0));
    }

    private static Artwork toArtwork(CatalogTrackResponse.Artwork a) {
        return a == null || a.url() == null ? null : new Artwork(a.url(), or(a.width(), 0), or(a.height(), 0));
    }

    /** An absent number in a lighter response shape is zero, not a failure. */
    private static <T> T or(T value, T fallback) {
        return value == null ? fallback : value;
    }
}

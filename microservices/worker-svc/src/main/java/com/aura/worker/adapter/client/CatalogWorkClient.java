package com.aura.worker.adapter.client;

import com.aura.worker.adapter.client.dto.DiscographyWork;
import com.aura.worker.config.ServiceEndpoints;
import com.aura.worker.domain.model.Artwork;
import com.aura.worker.domain.model.ProviderReference;
import com.aura.worker.domain.port.ProviderAlbum;
import com.aura.worker.domain.port.ProviderArtist;
import com.aura.worker.domain.port.ProviderTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives catalog-svc's discography work API: claim an artist, post back what TIDAL gave us, or say
 * we couldn't get it. This service never writes a catalog row itself — that is the whole point of
 * doing it over an API rather than over a shared datasource.
 */
@Component
public class CatalogWorkClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogWorkClient.class);
    private static final String BASE = "/api/v1/catalog/internal/discography";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CatalogWorkClient(RestClient.Builder builder, ServiceEndpoints endpoints,
                             CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone().baseUrl(endpoints.catalogUrl()).build();
        this.circuitBreaker = circuitBreakerFactory.create("catalog");
    }

    /**
     * @param lane {@code ON_DEMAND} takes only artists somebody has open right now
     * @return the artist to sync, or empty when this lane has nothing to do (catalog answers 204)
     */
    public Optional<DiscographyWork.Claim> claim(String lane) {
        return guarded("claim " + lane, () -> Optional.ofNullable(restClient.post()
                .uri(b -> b.path(BASE + "/claim").queryParam("lane", lane).build())
                .retrieve()
                .body(DiscographyWork.Claim.class)));
    }

    public DiscographyWork.IngestResult ingest(UUID artistId, List<ProviderTrack> tracks, String depth) {
        return guarded("ingest " + artistId, () -> restClient.post()
                .uri(BASE + "/{id}/tracks", artistId)
                .body(new DiscographyWork.Ingest(tracks.stream().map(CatalogWorkClient::toWire).toList(), depth))
                .retrieve()
                .body(DiscographyWork.IngestResult.class));
    }

    /** Provider unreachable — undo the claim so the artist keeps its place in the queue. */
    public void release(UUID artistId, String reason) {
        guarded("release " + artistId, () -> {
            restClient.post()
                    .uri(BASE + "/{id}/release", artistId)
                    .body(new DiscographyWork.Failure(reason))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public void markFailed(UUID artistId, String error) {
        guarded("fail " + artistId, () -> {
            restClient.post()
                    .uri(BASE + "/{id}/failed", artistId)
                    .body(new DiscographyWork.Failure(error))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private <T> T guarded(String what, java.util.function.Supplier<T> call) {
        try {
            return circuitBreaker.run(call::get);
        } catch (RuntimeException e) {
            log.warn("catalog-svc unreachable for {}", what, e);
            throw new CatalogUnavailableException(e);
        }
    }

    /** catalog-svc is down. The worker backs off — there is no point fetching what nobody can store. */
    public static class CatalogUnavailableException extends RuntimeException {
        public CatalogUnavailableException(Throwable cause) {
            super("catalog-svc is currently unavailable", cause);
        }
    }

    // ── Domain → wire ──────────────────────────────────────────────────────────────────────

    private static DiscographyWork.Track toWire(ProviderTrack t) {
        return new DiscographyWork.Track(toWire(t.ref()), t.title(), t.version(), t.durationMs(), t.isrc(),
                t.explicit(), t.popularity(), toWire(t.album()),
                t.artists() == null ? List.of() : t.artists().stream().map(CatalogWorkClient::toWire).toList(),
                t.volumeNumber(), t.trackNumber());
    }

    private static DiscographyWork.Album toWire(ProviderAlbum a) {
        if (a == null) return null;
        return new DiscographyWork.Album(toWire(a.ref()), a.title(), a.type() == null ? null : a.type().name(),
                a.releaseDate(), toWire(a.artist()), toWire(a.artwork()), a.explicit(), a.numberOfTracks(), a.popularity());
    }

    private static DiscographyWork.Artist toWire(ProviderArtist a) {
        if (a == null) return null;
        return new DiscographyWork.Artist(toWire(a.ref()), a.name(), toWire(a.artwork()), a.popularity());
    }

    private static DiscographyWork.Ref toWire(ProviderReference ref) {
        return ref == null ? null : new DiscographyWork.Ref(ref.provider().name(), ref.providerResourceId());
    }

    private static DiscographyWork.Artwork toWire(Artwork a) {
        return a == null ? null : new DiscographyWork.Artwork(a.url(), a.width(), a.height());
    }
}

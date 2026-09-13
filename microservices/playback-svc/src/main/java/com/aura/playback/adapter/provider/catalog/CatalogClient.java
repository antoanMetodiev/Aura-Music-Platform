package com.aura.playback.adapter.provider.catalog;

import com.aura.playback.adapter.provider.catalog.dto.CatalogTrackResponse;
import com.aura.playback.domain.model.CanonicalTrack;
import com.aura.playback.domain.port.CatalogTrackLookup;
import com.aura.playback.domain.service.CatalogServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads canonical track metadata straight from catalog-svc (service-to-service, not through the
 * gateway — Project-Info.md §16: the resolver owns none of this data itself).
 */
@Component
public class CatalogClient implements CatalogTrackLookup {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CatalogClient(RestClient.Builder builder, CatalogClientProperties properties,
                          CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone().baseUrl(properties.baseUrl()).build();
        this.circuitBreaker = circuitBreakerFactory.create("catalog");
    }

    @Override
    public Optional<CanonicalTrack> findTrack(UUID trackId) {
        try {
            return circuitBreaker.run(() -> fetch(trackId));
        } catch (RuntimeException e) {
            log.warn("Unable to reach catalog-svc for track {}", trackId, e);
            throw new CatalogServiceUnavailableException(e);
        }
    }

    private Optional<CanonicalTrack> fetch(UUID trackId) {
        try {
            CatalogTrackResponse response = restClient.get()
                    .uri("/api/v1/catalog/tracks/{id}", trackId)
                    .retrieve()
                    .body(CatalogTrackResponse.class);
            return Optional.ofNullable(response).map(CatalogClient::toCanonicalTrack);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.resolve(e.getStatusCode().value()) == HttpStatus.NOT_FOUND) return Optional.empty();
            throw e;
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw e;
        }
    }

    private static CanonicalTrack toCanonicalTrack(CatalogTrackResponse response) {
        List<String> artistNames = response.artists() == null
                ? List.of()
                : response.artists().stream().map(CatalogTrackResponse.ArtistSummary::name).toList();
        return new CanonicalTrack(response.id(), response.title(), artistNames, response.durationMs(), response.isrc());
    }
}

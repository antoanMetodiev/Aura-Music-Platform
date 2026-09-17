package com.aura.worker.adapter.client;

import com.aura.worker.adapter.client.dto.GraphWork;
import com.aura.worker.config.ServiceEndpoints;
import com.aura.worker.domain.port.ProviderSimilarArtist;
import com.aura.worker.domain.port.ProviderTag;
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
 * Drives recommendation-svc's graph work API. We send the provider's answer exactly as it arrived —
 * names, matches, tags — and that service decides which of its artists those names mean. Resolution
 * needs the catalog and its own spelling rules, so it does not belong on this side.
 */
@Component
public class RecommendationWorkClient {

    private static final Logger log = LoggerFactory.getLogger(RecommendationWorkClient.class);
    private static final String BASE = "/api/v1/recommendations/internal/graph";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public RecommendationWorkClient(RestClient.Builder builder, ServiceEndpoints endpoints,
                                    CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone().baseUrl(endpoints.recommendationUrl()).build();
        this.circuitBreaker = circuitBreakerFactory.create("recommendation");
    }

    /** @return the artist whose graph to build, or empty when every artist is fresh (204) */
    public Optional<GraphWork.Claim> claim() {
        return guarded("claim", () -> Optional.ofNullable(restClient.post()
                .uri(BASE + "/claim")
                .retrieve()
                .body(GraphWork.Claim.class)));
    }

    public GraphWork.IngestResult ingest(UUID artistId, List<ProviderSimilarArtist> similar, List<ProviderTag> tags) {
        GraphWork.Ingest body = new GraphWork.Ingest(
                similar.stream().map(s -> new GraphWork.Similar(s.name(), s.match(), s.mbid(), s.url())).toList(),
                tags.stream().map(t -> new GraphWork.Tag(t.name(), t.count())).toList());
        return guarded("ingest " + artistId, () -> restClient.post()
                .uri(BASE + "/{id}", artistId)
                .body(body)
                .retrieve()
                .body(GraphWork.IngestResult.class));
    }

    public void release(UUID artistId, String reason) {
        guarded("release " + artistId, () -> {
            restClient.post().uri(BASE + "/{id}/release", artistId)
                    .body(new GraphWork.Failure(reason))
                    .retrieve().toBodilessEntity();
            return null;
        });
    }

    public void markFailed(UUID artistId, String error) {
        guarded("fail " + artistId, () -> {
            restClient.post().uri(BASE + "/{id}/failed", artistId)
                    .body(new GraphWork.Failure(error))
                    .retrieve().toBodilessEntity();
            return null;
        });
    }

    /** Asks for another page of artists to be pulled from the catalog into the queue. No provider call. */
    public GraphWork.SeedResult seed() {
        return guarded("seed", () -> restClient.post()
                .uri(BASE + "/seed")
                .retrieve()
                .body(GraphWork.SeedResult.class));
    }

    private <T> T guarded(String what, java.util.function.Supplier<T> call) {
        try {
            return circuitBreaker.run(call::get);
        } catch (RuntimeException e) {
            log.warn("recommendation-svc unreachable for {}", what, e);
            throw new RecommendationUnavailableException(e);
        }
    }

    /** recommendation-svc is down — there is nothing to claim and nowhere to put an answer. */
    public static class RecommendationUnavailableException extends RuntimeException {
        public RecommendationUnavailableException(Throwable cause) {
            super("recommendation-svc is currently unavailable", cause);
        }
    }
}

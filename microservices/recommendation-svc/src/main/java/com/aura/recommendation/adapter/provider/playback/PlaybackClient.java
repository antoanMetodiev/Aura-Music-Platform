package com.aura.recommendation.adapter.provider.playback;

import com.aura.recommendation.adapter.provider.playback.dto.PlaybackSourceResponse;
import com.aura.recommendation.domain.port.PlayabilityLookup;
import com.aura.recommendation.domain.service.PlaybackUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link PlayabilityLookup} over playback-svc's batch {@code GET /sources} — one call for a whole
 * feed's candidates, and a read-only one: it returns the verified sources that already exist and
 * resolves nothing, so ranking fifty candidates never costs a YouTube search (Project-Info.md §20).
 */
@Component
public class PlaybackClient implements PlayabilityLookup {

    private static final Logger log = LoggerFactory.getLogger(PlaybackClient.class);
    /** playback-svc caps a request at 500 ids; stay well under it and page. */
    private static final int BATCH = 400;
    private static final ParameterizedTypeReference<List<PlaybackSourceResponse>> SOURCES = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public PlaybackClient(RestClient.Builder builder, PlaybackClientProperties properties,
                          CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone().baseUrl(properties.baseUrl()).build();
        this.circuitBreaker = circuitBreakerFactory.create("playback");
    }

    @Override
    public Set<UUID> playableAmong(Collection<UUID> trackIds) {
        if (trackIds.isEmpty()) return Set.of();
        List<UUID> distinct = trackIds.stream().distinct().toList();
        try {
            return circuitBreaker.run(() -> {
                Set<UUID> playable = new java.util.HashSet<>();
                for (int from = 0; from < distinct.size(); from += BATCH) {
                    List<UUID> page = distinct.subList(from, Math.min(from + BATCH, distinct.size()));
                    List<PlaybackSourceResponse> body = restClient.get()
                            .uri(b -> b.path("/api/v1/playback/sources")
                                    .queryParam("trackIds", page.stream().map(UUID::toString).toList())
                                    .build())
                            .retrieve()
                            .body(SOURCES);
                    if (body != null) {
                        playable.addAll(body.stream().map(PlaybackSourceResponse::trackId).collect(Collectors.toSet()));
                    }
                }
                return playable;
            });
        } catch (RuntimeException e) {
            log.warn("Unable to reach playback-svc to check playability of {} tracks", distinct.size(), e);
            throw new PlaybackUnavailableException(e);
        }
    }
}

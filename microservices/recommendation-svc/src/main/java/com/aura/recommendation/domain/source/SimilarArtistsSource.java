package com.aura.recommendation.domain.source;

import com.aura.recommendation.config.RecommendationProperties;
import com.aura.recommendation.domain.model.CandidateOrigin;
import com.aura.recommendation.domain.port.SimilarityGraphStore;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The taste graph itself: artists the provider's listeners also like. The primary source — everything
 * else is there for when this one is thin.
 */
@Order(1)
@Component
public class SimilarArtistsSource implements ArtistCandidateSource {

    private final SimilarityGraphStore graph;
    private final RecommendationProperties properties;

    public SimilarArtistsSource(SimilarityGraphStore graph, RecommendationProperties properties) {
        this.graph = graph;
        this.properties = properties;
    }

    @Override
    public CandidateOrigin origin() {
        return CandidateOrigin.SIMILAR;
    }

    @Override
    public List<ArtistCandidate> candidates(CandidateRequest request) {
        List<ArtistCandidate> out = new ArrayList<>();
        int perSeed = Math.max(1, properties.maxSimilarArtists() / Math.max(1, request.seedArtistIds().size()));
        for (UUID seed : request.seedArtistIds()) {
            for (SimilarityGraphStore.Neighbour n : graph.neighbours(seed, perSeed, properties.reverseEdgeFactor())) {
                CandidateOrigin origin = n.reverse() ? CandidateOrigin.REVERSE_SIMILAR : CandidateOrigin.SIMILAR;
                out.add(new ArtistCandidate(n.artistId(), n.score(), origin, seed));
            }
        }
        return out;
    }
}

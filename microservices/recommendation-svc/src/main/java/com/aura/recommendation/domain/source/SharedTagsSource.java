package com.aura.recommendation.domain.source;

import com.aura.recommendation.config.RecommendationProperties;
import com.aura.recommendation.domain.model.CandidateOrigin;
import com.aura.recommendation.domain.port.SimilarityGraphStore;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The genre axis: artists carrying the seed's tags. Reaches artists the similarity graph never
 * mentions — which is most of a Bulgarian catalogue, where Last.fm's edges run thin but the tags
 * ("pop folk", "bulgarian") still line up.
 */
@Order(2)
@Component
public class SharedTagsSource implements ArtistCandidateSource {

    private final SimilarityGraphStore graph;
    private final RecommendationProperties properties;

    public SharedTagsSource(SimilarityGraphStore graph, RecommendationProperties properties) {
        this.graph = graph;
        this.properties = properties;
    }

    @Override
    public CandidateOrigin origin() {
        return CandidateOrigin.SHARED_TAG;
    }

    @Override
    public List<ArtistCandidate> candidates(CandidateRequest request) {
        if (request.seedTags().isEmpty()) return List.of();
        List<String> tags = request.seedTags().stream().limit(properties.seedTagsUsed()).toList();
        return graph.artistsSharingTags(tags, request.seedArtistIds(), properties.maxSharedTagArtists()).stream()
                .map(n -> new ArtistCandidate(n.artistId(), n.score(), CandidateOrigin.SHARED_TAG,
                        request.seedArtistIds().isEmpty() ? null : request.seedArtistIds().getFirst()))
                .toList();
    }
}

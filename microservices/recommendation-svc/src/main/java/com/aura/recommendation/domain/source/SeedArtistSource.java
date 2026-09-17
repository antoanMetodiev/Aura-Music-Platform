package com.aura.recommendation.domain.source;

import com.aura.recommendation.domain.model.CandidateOrigin;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The seed artists themselves, at full weight. An artist's radio that never plays the artist is not
 * a radio; a "fans also like" list that does is wrong — hence {@code includeSeeds} on the request.
 */
@Order(0)
@Component
public class SeedArtistSource implements ArtistCandidateSource {

    @Override
    public CandidateOrigin origin() {
        return CandidateOrigin.SEED;
    }

    @Override
    public List<ArtistCandidate> candidates(CandidateRequest request) {
        if (!request.includeSeeds()) return List.of();
        return request.seedArtistIds().stream()
                .map(id -> new ArtistCandidate(id, 1.0, CandidateOrigin.SEED, id))
                .toList();
    }
}

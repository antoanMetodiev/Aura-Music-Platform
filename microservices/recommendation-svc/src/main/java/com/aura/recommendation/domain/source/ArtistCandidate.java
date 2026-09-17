package com.aura.recommendation.domain.source;

import com.aura.recommendation.domain.model.CandidateOrigin;

import java.util.UUID;

/**
 * An artist a source proposes, before anyone has looked at their tracks. Deliberately just an id and
 * a weight: hydrating forty artists to then throw most of them away is the kind of work a candidate
 * stage must not do.
 *
 * @param seedArtistId which seed brought them in — the "because you like X" of the final response
 */
public record ArtistCandidate(UUID artistId, double score, CandidateOrigin origin, UUID seedArtistId) {

    public ArtistCandidate merge(ArtistCandidate other) {
        // The strongest signal wins, with half of the weaker one added: several weak reasons are
        // worth something, but never more than one strong one.
        double combined = Math.max(score, other.score()) + Math.min(score, other.score()) / 2;
        return score >= other.score()
                ? new ArtistCandidate(artistId, combined, origin, seedArtistId)
                : new ArtistCandidate(artistId, combined, other.origin(), other.seedArtistId());
    }
}

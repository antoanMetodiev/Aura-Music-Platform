package com.aura.recommendation.domain.model;

/**
 * A candidate artist with the weight the candidate sources gave them. Several sources may propose
 * the same artist; {@link #merge} is how their evidence adds up (the strongest origin wins the
 * label, the scores combine with diminishing returns so three weak signals don't beat one strong one).
 */
public record ScoredArtist(ArtistRef artist, double score, CandidateOrigin origin) {

    public ScoredArtist merge(ScoredArtist other) {
        double combined = Math.max(score, other.score()) + Math.min(score, other.score()) / 2;
        CandidateOrigin strongest = score >= other.score() ? origin : other.origin();
        return new ScoredArtist(artist, combined, strongest);
    }

    public ScoredArtist withScore(double newScore) {
        return new ScoredArtist(artist, newScore, origin);
    }
}

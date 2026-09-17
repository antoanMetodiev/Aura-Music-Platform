package com.aura.recommendation.domain.model;

/** Which candidate source put an artist in the running — kept through ranking so the UI can say why. */
public enum CandidateOrigin {
    /** The seed artist themselves (an artist's own radio opens with their own music). */
    SEED,
    /** The provider lists this artist as similar to the seed. */
    SIMILAR,
    /** The provider lists the <em>seed</em> as similar to this artist — the reverse edge, weighted lower. */
    REVERSE_SIMILAR,
    /** Shares community tags with the seed, without a similarity edge. */
    SHARED_TAG,
    /** Popular in our own catalog — the cold-start filler when the graph has nothing. */
    POPULAR
}

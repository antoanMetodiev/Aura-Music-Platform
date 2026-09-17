package com.aura.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Candidate-generation and ranking weights (Project-Info.md §24: the first version is rule-based, and
 * the rules live in configuration rather than in code — the same discipline as playback-svc's
 * {@code MatchingProperties}). Everything a feed does is a weighted sum of these, so a bad feed is
 * tuned here, not rewritten.
 */
@Validated
@ConfigurationProperties(prefix = "aura.recommendations")
public record RecommendationProperties(

        // ── Candidate generation ───────────────────────────────────────────────────────────

        /** How many neighbour artists a seed contributes at most. */
        @DefaultValue("40") int maxSimilarArtists,
        /**
         * A reverse edge (they were called similar to us, not the other way round) counts this much of
         * a forward one. Below 1 because the provider's graph leans towards the famous: everyone is
         * "similar to" Adele, which would otherwise make every radio an Adele radio.
         */
        @DefaultValue("0.6") double reverseEdgeFactor,
        /** How many artists the shared-tag source may add when the similarity edges are thin. */
        @DefaultValue("20") int maxSharedTagArtists,
        /** Tags of the seed used for the shared-tag source (its heaviest ones). */
        @DefaultValue("5") int seedTagsUsed,
        /** Tracks pulled from the catalog per candidate artist before ranking. */
        @DefaultValue("8") int tracksPerCandidateArtist,

        // ── Ranking ────────────────────────────────────────────────────────────────────────

        /** Weight of the candidate artist's own score (similarity, 0..1) in a track's score. */
        @DefaultValue("100") double artistScoreWeight,
        /** Weight of the track's provider popularity (0..1). Keeps a known song ahead of a deep cut of an equally similar artist. */
        @DefaultValue("35") double trackPopularityWeight,
        /** Added to a track playback-svc can already play. */
        @DefaultValue("15") double playableBonus,
        /** How much of a track's score survives being the artist's Nth pick — stops one artist filling the feed. */
        @DefaultValue("0.55") double perArtistDecay,
        /** Hard cap on tracks from one artist in a feed. */
        @DefaultValue("3") int maxTracksPerArtist,
        /**
         * Drop tracks with no verified playback source. Playing is the point (§18: "playback
         * unavailable" beats the wrong song, and a recommendation that cannot be played is neither).
         * When playback-svc is unreachable the feed is served unfiltered rather than empty.
         */
        @DefaultValue("true") boolean dropUnplayable,

        // ── Serving ────────────────────────────────────────────────────────────────────────

        /** Feeds are recomputed at most this often per seed; in memory only (Project-Info.md §30 — Redis later). */
        @DefaultValue("15m") Duration feedMemoryTtl,
        @DefaultValue("500") int feedMemoryEntries,
        @DefaultValue("20") int defaultLimit,
        @DefaultValue("100") int maxLimit
) {
}

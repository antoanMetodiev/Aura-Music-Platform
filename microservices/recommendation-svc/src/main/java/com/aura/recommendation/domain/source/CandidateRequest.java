package com.aura.recommendation.domain.source;

import java.util.List;
import java.util.UUID;

/**
 * What a feed is seeded from. Today the seeds are artists (an artist's radio, a track's artist);
 * when likes and listening history arrive, a taste profile is simply a longer seed list with weights
 * — the sources below don't change.
 *
 * @param seedTags     the seeds' own tags, precomputed once so every source doesn't re-read them
 * @param includeSeeds whether the seed artists themselves are candidates (a radio opens with them;
 *                     a "fans also like" list must not)
 */
public record CandidateRequest(List<UUID> seedArtistIds, List<String> seedTags, boolean includeSeeds, int limit) {
}

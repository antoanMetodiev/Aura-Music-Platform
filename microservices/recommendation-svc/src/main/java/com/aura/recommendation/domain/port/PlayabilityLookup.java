package com.aura.recommendation.domain.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Which tracks playback-svc already has a verified source for. A recommendation nobody can play is
 * worse than no recommendation, so this decides what survives ranking.
 *
 * <p>Read-only by contract: asking must never make playback-svc resolve anything, or a feed of fifty
 * candidates would cost fifty YouTube searches (Project-Info.md §20).
 */
public interface PlayabilityLookup {

    /**
     * @return the subset of {@code trackIds} that are playable right now
     * @throws com.aura.recommendation.domain.service.PlaybackUnavailableException playback-svc is down;
     *         the caller decides whether to serve unfiltered or to fail
     */
    Set<UUID> playableAmong(Collection<UUID> trackIds);
}

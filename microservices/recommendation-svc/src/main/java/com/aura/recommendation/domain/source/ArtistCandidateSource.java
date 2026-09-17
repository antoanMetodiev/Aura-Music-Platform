package com.aura.recommendation.domain.source;

import com.aura.recommendation.domain.model.CandidateOrigin;

import java.util.List;

/**
 * One way of proposing artists for a feed. Sources are Spring beans in {@code @Order}, exactly like
 * playback-svc's known-video lookups: adding a signal (friends are listening to them, the user liked
 * them last week, a vector neighbour) means adding a bean here, not touching the ranking or the API.
 *
 * <p>A source returns ids and weights only — never provider calls, never catalog reads. Everything it
 * needs is precomputed in our own graph, which is what lets a feed be served in one round of SQL.
 */
public interface ArtistCandidateSource {

    CandidateOrigin origin();

    List<ArtistCandidate> candidates(CandidateRequest request);
}

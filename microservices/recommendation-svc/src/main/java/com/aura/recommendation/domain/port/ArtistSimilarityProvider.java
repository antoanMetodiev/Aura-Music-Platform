package com.aura.recommendation.domain.port;

import com.aura.recommendation.domain.model.Provider;

import java.util.List;

/**
 * Where the taste graph comes from (Project-Info.md §36: business logic must not know it is Last.fm).
 * Last.fm today; ListenBrainz, or embeddings computed from our own listening history later, slot in
 * here without the ranking or the API changing.
 *
 * <p>Both calls are made <em>only</em> by the background graph worker, never while serving a user
 * request — that is the whole point of precomputing the graph (§20's quota discipline applied to a
 * provider that allows 5 requests a second).
 *
 * @implNote implementations throw {@code ProviderUnavailableException} for an outage and return an
 *           empty list for "asked, they don't know this artist" — confusing the two would record an
 *           outage as "this artist has no neighbours", permanently.
 */
public interface ArtistSimilarityProvider {

    Provider provider();

    /** Artists whose listeners also like {@code artistName}, strongest first, at most {@code limit}. */
    List<ProviderSimilarArtist> similarTo(String artistName, int limit);

    /** Community tags for the artist, heaviest first. */
    List<ProviderTag> topTags(String artistName);
}

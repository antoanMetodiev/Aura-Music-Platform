package com.aura.recommendation.domain.port;

/**
 * A similar artist exactly as the provider names them - a <em>name</em>, not an id. Turning it into
 * one of our artists is {@code ArtistGraphService}'s job and the hard part of the whole integration.
 *
 * @param match 0..1, the provider's own similarity weight
 * @param mbid  MusicBrainz id when the provider has one (unused today; the sturdier key once
 *              catalog-svc carries MBIDs of its own)
 */
public record ProviderSimilarArtist(String name, double match, String mbid, String url) {
}

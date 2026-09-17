package com.aura.recommendation.domain.port;

import com.aura.recommendation.domain.model.ArtistRef;
import com.aura.recommendation.domain.model.TrackRef;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything this service needs from catalog-svc, over its public API — never its tables
 * (Project-Info.md §6). We own the edges between artists; catalog owns the artists.
 */
public interface CatalogLookup {

    Optional<ArtistRef> findArtist(UUID artistId);

    Optional<TrackRef> findTrack(UUID trackId);

    List<ArtistRef> findArtistsByIds(Collection<UUID> artistIds);

    /**
     * Provider names to our artists: one canonical artist per distinct name, names we don't have
     * simply absent from the result. The single most important call in the graph build.
     */
    List<ArtistRef> findArtistsByNames(Collection<String> names);

    /**
     * The artist's most popular tracks, <em>local only</em> — catalog-svc must not pull a discography
     * from TIDAL because we asked about twenty candidate artists at once.
     */
    List<TrackRef> topTracks(UUID artistId, int limit);

    /** Keyset page of canonical artists, most popular first. */
    List<ArtistRef> scanArtists(double popularityBelow, UUID afterId, int limit);

    /** Keyset page of tracks, most popular first — the cold-start "trending" source. */
    List<TrackRef> scanTracksByPopularity(double popularityBelow, UUID afterId, int limit);
}

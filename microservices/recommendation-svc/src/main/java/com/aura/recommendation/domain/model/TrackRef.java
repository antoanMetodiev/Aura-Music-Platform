package com.aura.recommendation.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * A track as catalog-svc hands it to us, carried through to our own responses unchanged so the
 * frontend can map a recommended track with exactly the same code it maps a search result with.
 */
public record TrackRef(
        UUID id,
        String title,
        String version,
        long durationMs,
        String isrc,
        boolean explicit,
        List<ArtistRef> artists,
        AlbumRef album,
        Artwork artwork,
        double popularity
) {
    public ArtistRef primaryArtist() {
        return artists == null || artists.isEmpty() ? null : artists.getFirst();
    }
}

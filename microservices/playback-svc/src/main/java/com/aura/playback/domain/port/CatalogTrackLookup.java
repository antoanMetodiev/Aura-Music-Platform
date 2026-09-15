package com.aura.playback.domain.port;

import com.aura.playback.domain.model.CanonicalTrack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the canonical track (title/artists/duration/ISRC) from catalog-svc. Playback Resolver owns
 * no track metadata of its own (Project-Info.md §16) — it only ever asks catalog-svc for it.
 */
public interface CatalogTrackLookup {

    Optional<CanonicalTrack> findTrack(UUID trackId);

    /**
     * A page of the whole catalog most-popular-first, strictly after {@code (popularityBelow, afterId)} in
     * {@code (popularity DESC, id DESC)} order; the caller continues from the last item's
     * {@code popularity}/{@code id}.
     */
    List<ScannedTrack> scanByPopularity(double popularityBelow, UUID afterId, int limit);

    /** {@code popularity} is the provider's 0..1 signal — only meaningful as an ordering. */
    record ScannedTrack(CanonicalTrack track, double popularity) {
    }
}

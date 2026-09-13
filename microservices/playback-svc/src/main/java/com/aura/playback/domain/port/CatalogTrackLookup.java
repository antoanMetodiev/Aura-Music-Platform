package com.aura.playback.domain.port;

import com.aura.playback.domain.model.CanonicalTrack;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the canonical track (title/artists/duration/ISRC) from catalog-svc. Playback Resolver owns
 * no track metadata of its own (Project-Info.md §16) — it only ever asks catalog-svc for it.
 */
public interface CatalogTrackLookup {

    Optional<CanonicalTrack> findTrack(UUID trackId);
}

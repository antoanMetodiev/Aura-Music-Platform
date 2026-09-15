package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.Lyrics;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for the per-track lyrics cache ({@code catalog.track_lyrics}). */
public interface LyricsStore {

    /**
     * Empty = never looked up. A present entry with {@code lyrics == null} is a recorded miss
     * ("asked the provider at {@code fetchedAt}, it had nothing").
     */
    Optional<Cached> find(UUID trackId);

    void save(Lyrics lyrics);

    void saveMiss(UUID trackId, Instant at);

    record Cached(Lyrics lyrics, Instant fetchedAt) {
        public boolean isMiss() {
            return lyrics == null;
        }
    }
}

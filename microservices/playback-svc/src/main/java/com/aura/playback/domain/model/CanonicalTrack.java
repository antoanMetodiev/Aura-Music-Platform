package com.aura.playback.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * The subset of a catalog-svc track the matcher needs (Project-Info.md §17: title, artist,
 * duration, ISRC when available). Fetched fresh from catalog-svc for every resolution — Playback
 * Resolver never caches catalog metadata itself, only the resolved source (§16: it "does not
 * decide what the song is").
 */
public record CanonicalTrack(
        UUID id,
        String title,
        List<String> artistNames,
        long durationMs,
        String isrc
) {
    public String primaryArtist() {
        return artistNames.isEmpty() ? "" : artistNames.get(0);
    }
}

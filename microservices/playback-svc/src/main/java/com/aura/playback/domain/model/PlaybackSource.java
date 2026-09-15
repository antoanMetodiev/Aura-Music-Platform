package com.aura.playback.domain.model;

import java.time.Instant;
import java.util.UUID;

/** The resolved playback source for one canonical track (Project-Info.md §19 {@code playback.track_sources}). */
public record PlaybackSource(
        UUID id,
        UUID trackId,
        PlaybackProvider provider,
        /** The recording's ISRC at resolve time (nullable) — lets tracks that are re-releases of the same recording share this source. */
        String isrc,
        String providerResourceId,
        String title,
        String channelId,
        String channelTitle,
        long durationMs,
        int matchScore,
        MatchMethod matchMethod,
        boolean verified,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

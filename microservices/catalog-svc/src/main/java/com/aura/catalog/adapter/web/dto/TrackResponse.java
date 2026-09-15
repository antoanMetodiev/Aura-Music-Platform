package com.aura.catalog.adapter.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrackResponse(
        UUID id,
        String title,
        String version,
        long durationMs,
        String isrc,
        boolean explicit,
        List<ArtistSummaryResponse> artists,
        AlbumSummaryResponse album,
        ArtworkResponse artwork,
        Integer volumeNumber,
        Integer trackNumber,
        /** Provider popularity, 0..1 (TIDAL). Relative ordering signal only — not a play count. */
        double popularity,
        /** When we first stored the track — the cursor for {@code GET /tracks/scan}. */
        Instant createdAt
) {
}

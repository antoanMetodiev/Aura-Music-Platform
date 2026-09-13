package com.aura.catalog.adapter.web.dto;

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
        ArtworkResponse artwork
) {
}

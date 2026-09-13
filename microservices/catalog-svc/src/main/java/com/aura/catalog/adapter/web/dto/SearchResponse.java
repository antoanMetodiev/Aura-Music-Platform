package com.aura.catalog.adapter.web.dto;

import java.util.List;

public record SearchResponse(
        String query,
        List<TrackResponse> tracks,
        List<AlbumSummaryResponse> albums,
        List<ArtistSummaryResponse> artists
) {
}

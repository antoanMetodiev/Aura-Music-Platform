package com.aura.catalog.adapter.web.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AlbumResponse(
        UUID id,
        String title,
        String albumType,
        LocalDate releaseDate,
        ArtistSummaryResponse artist,
        ArtworkResponse artwork,
        boolean explicit,
        int numberOfTracks
) {
}

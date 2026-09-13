package com.aura.catalog.adapter.web.dto;

import java.util.UUID;

public record AlbumSummaryResponse(UUID id, String title, ArtistSummaryResponse artist, ArtworkResponse artwork, Integer releaseYear) {
}

package com.aura.catalog.adapter.web.dto;

import java.util.UUID;

public record ArtistSummaryResponse(UUID id, String name, ArtworkResponse artwork) {
}

package com.aura.catalog.adapter.web.dto;

import java.util.UUID;

public record ArtistResponse(UUID id, String name, ArtworkResponse artwork, double popularity) {
}

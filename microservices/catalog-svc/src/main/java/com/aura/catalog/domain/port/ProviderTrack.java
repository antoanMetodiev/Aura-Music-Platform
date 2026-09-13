package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.ProviderReference;

import java.util.List;

public record ProviderTrack(
        ProviderReference ref,
        String title,
        String version,
        long durationMs,
        String isrc,
        boolean explicit,
        double popularity,
        ProviderAlbum album,
        List<ProviderArtist> artists
) {
}

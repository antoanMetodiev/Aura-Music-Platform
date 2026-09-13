package com.aura.catalog.domain.port;

import java.util.List;

public record ProviderSearchResult(List<ProviderTrack> tracks, List<ProviderAlbum> albums, List<ProviderArtist> artists) {
    public static ProviderSearchResult empty() {
        return new ProviderSearchResult(List.of(), List.of(), List.of());
    }
}

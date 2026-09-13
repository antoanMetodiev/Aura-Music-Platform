package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.AlbumType;
import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.ProviderReference;

import java.time.LocalDate;

public record ProviderAlbum(
        ProviderReference ref,
        String title,
        AlbumType type,
        LocalDate releaseDate,
        ProviderArtist artist,
        Artwork artwork,
        boolean explicit,
        int numberOfTracks,
        double popularity
) {
}

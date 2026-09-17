package com.aura.worker.domain.port;

import com.aura.worker.domain.model.AlbumType;
import com.aura.worker.domain.model.Artwork;
import com.aura.worker.domain.model.ProviderReference;

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

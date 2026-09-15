package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.ArtistAbout;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for the per-artist "About" cache ({@code catalog.artist_about}). An empty {@link ArtistAbout} is a recorded miss. */
public interface ArtistAboutStore {

    Optional<ArtistAbout> find(UUID artistId);

    void save(ArtistAbout about);
}

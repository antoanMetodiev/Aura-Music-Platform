package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.model.Track;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for our own catalog cache (Project-Info.md §14). Upserts are keyed on the provider reference,
 * so re-discovering the same provider entity refreshes it instead of duplicating it.
 */
public interface CatalogStore {

    Optional<Track> findTrackById(UUID id);

    Optional<Track> findTrackByProviderRef(ProviderReference ref);

    List<Track> findTracksByIsrc(String isrc);

    Optional<Album> findAlbumById(UUID id);

    Optional<Album> findAlbumByProviderRef(ProviderReference ref);

    Optional<Artist> findArtistById(UUID id);

    Optional<Artist> findArtistByProviderRef(ProviderReference ref);

    Track upsertTrack(ProviderTrack track);

    Album upsertAlbum(ProviderAlbum album);

    Artist upsertArtist(ProviderArtist artist);
}

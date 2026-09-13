package com.aura.catalog.adapter.persistence;

import com.aura.catalog.adapter.persistence.repository.AlbumRepository;
import com.aura.catalog.adapter.persistence.repository.ArtistRepository;
import com.aura.catalog.adapter.persistence.repository.TrackRepository;
import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.model.Track;
import com.aura.catalog.domain.port.CatalogStore;
import com.aura.catalog.domain.port.ProviderAlbum;
import com.aura.catalog.domain.port.ProviderArtist;
import com.aura.catalog.domain.port.ProviderTrack;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link CatalogStore} adapter: the boundary between the domain and our own PostgreSQL cache
 * (Project-Info.md §14). Pure delegation — the actual SQL and the artist→album→track upsert
 * cascade live in the three {@code *Repository} collaborators.
 */
@Component
public class JdbcCatalogStore implements CatalogStore {

    private final TrackRepository tracks;
    private final AlbumRepository albums;
    private final ArtistRepository artists;

    public JdbcCatalogStore(TrackRepository tracks, AlbumRepository albums, ArtistRepository artists) {
        this.tracks = tracks;
        this.albums = albums;
        this.artists = artists;
    }

    @Override
    public Optional<Track> findTrackById(UUID id) {
        return tracks.findById(id);
    }

    @Override
    public Optional<Track> findTrackByProviderRef(ProviderReference ref) {
        return tracks.findByProviderRef(ref);
    }

    @Override
    public List<Track> findTracksByIsrc(String isrc) {
        return tracks.findByIsrc(isrc);
    }

    @Override
    public Optional<Album> findAlbumById(UUID id) {
        return albums.findById(id);
    }

    @Override
    public Optional<Album> findAlbumByProviderRef(ProviderReference ref) {
        return albums.findByProviderRef(ref);
    }

    @Override
    public Optional<Artist> findArtistById(UUID id) {
        return artists.findById(id);
    }

    @Override
    public Optional<Artist> findArtistByProviderRef(ProviderReference ref) {
        return artists.findByProviderRef(ref);
    }

    @Override
    public Track upsertTrack(ProviderTrack track) {
        return tracks.upsert(track);
    }

    @Override
    public Album upsertAlbum(ProviderAlbum album) {
        return albums.upsert(album);
    }

    @Override
    public Artist upsertArtist(ProviderArtist artist) {
        return artists.upsert(artist);
    }
}

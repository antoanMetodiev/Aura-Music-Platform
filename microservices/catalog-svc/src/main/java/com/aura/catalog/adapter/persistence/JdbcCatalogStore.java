package com.aura.catalog.adapter.persistence;

import com.aura.catalog.adapter.persistence.repository.AlbumRepository;
import com.aura.catalog.adapter.persistence.repository.ArtistRepository;
import com.aura.catalog.adapter.persistence.repository.CatalogBatchWriter;
import com.aura.catalog.adapter.persistence.repository.LocalSearchRepository;
import com.aura.catalog.adapter.persistence.repository.SearchResultRepository;
import com.aura.catalog.adapter.persistence.repository.TrackRepository;
import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.CachedSearch;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.model.SearchType;
import com.aura.catalog.domain.model.Track;
import com.aura.catalog.domain.port.CatalogStore;
import com.aura.catalog.domain.port.ProviderAlbum;
import com.aura.catalog.domain.port.ProviderArtist;
import com.aura.catalog.domain.port.ProviderTrack;
import com.aura.catalog.domain.port.UpsertedBatch;
import org.springframework.stereotype.Component;

import java.util.Collection;
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
    private final SearchResultRepository searchResults;
    private final CatalogBatchWriter batchWriter;
    private final LocalSearchRepository localSearch;

    public JdbcCatalogStore(TrackRepository tracks, AlbumRepository albums, ArtistRepository artists,
                            SearchResultRepository searchResults, CatalogBatchWriter batchWriter,
                            LocalSearchRepository localSearch) {
        this.localSearch = localSearch;
        this.tracks = tracks;
        this.albums = albums;
        this.artists = artists;
        this.searchResults = searchResults;
        this.batchWriter = batchWriter;
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
    public List<Track> findTracksByAlbumId(UUID albumId) {
        return tracks.findByAlbumId(albumId);
    }

    @Override
    public List<Track> findTracksCreatedAfter(java.time.Instant createdAfter, UUID afterId, int limit) {
        return tracks.findCreatedAfter(createdAfter, afterId, limit);
    }

    @Override
    public void markAlbumTracksSynced(UUID albumId) {
        albums.markTracksSynced(albumId);
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
        return batchWriter.upsert(List.of(track), List.of(), List.of()).tracks().getFirst();
    }

    @Override
    public Album upsertAlbum(ProviderAlbum album) {
        return batchWriter.upsert(List.of(), List.of(album), List.of()).albums().getFirst();
    }

    @Override
    public Artist upsertArtist(ProviderArtist artist) {
        return batchWriter.upsert(List.of(), List.of(), List.of(artist)).artists().getFirst();
    }

    @Override
    public UpsertedBatch upsertBatch(List<ProviderTrack> trackList, List<ProviderAlbum> albumList, List<ProviderArtist> artistList) {
        return batchWriter.upsert(trackList, albumList, artistList);
    }

    @Override
    public List<Track> findTracksByIds(Collection<UUID> ids) {
        return tracks.findByIds(ids);
    }

    @Override
    public List<Album> findAlbumsByIds(Collection<UUID> ids) {
        return albums.findByIds(ids);
    }

    @Override
    public List<Artist> findArtistsByIds(Collection<UUID> ids) {
        return artists.findByIds(ids);
    }

    @Override
    public Optional<CachedSearch> findCachedSearch(String normalizedQuery, SearchType type) {
        return searchResults.find(normalizedQuery, type);
    }

    @Override
    public void saveCachedSearch(String normalizedQuery, SearchType type, List<UUID> entityIds) {
        searchResults.save(normalizedQuery, type, entityIds);
    }

    @Override
    public List<Track> searchTracksLocally(String query, int limit) {
        return tracks.findByIds(localSearch.tracks(query, limit));
    }

    @Override
    public List<Album> searchAlbumsLocally(String query, int limit) {
        return albums.findByIds(localSearch.albums(query, limit));
    }

    @Override
    public List<Artist> searchArtistsLocally(String query, int limit) {
        return artists.findByIds(localSearch.artists(query, limit));
    }
}

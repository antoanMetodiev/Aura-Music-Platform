package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.CachedSearch;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.model.SearchType;
import com.aura.catalog.domain.model.Track;

import java.util.Collection;
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

    /** In album play order (volume, then track number); tracks with no known position come last. */
    List<Track> findTracksByAlbumId(UUID albumId);

    /** Every track the artist appears on, most popular first. */
    List<Track> findTracksByArtistId(UUID artistId, int limit);

    /** Albums credited to the artist, newest first. */
    List<Album> findAlbumsByArtistId(UUID artistId);

    /** Keyset page over every track in insertion order, strictly after {@code (createdAfter, afterId)}. */
    List<Track> findTracksCreatedAfter(java.time.Instant createdAfter, UUID afterId, int limit);

    void markAlbumTracksSynced(UUID albumId);

    Optional<Artist> findArtistById(UUID id);

    Optional<Artist> findArtistByProviderRef(ProviderReference ref);

    Track upsertTrack(ProviderTrack track);

    Album upsertAlbum(ProviderAlbum album);

    Artist upsertArtist(ProviderArtist artist);

    /** Persists a whole provider result set in a fixed handful of statements — see {@code CatalogBatchWriter}. */
    UpsertedBatch upsertBatch(List<ProviderTrack> tracks, List<ProviderAlbum> albums, List<ProviderArtist> artists);

    // ── Batch reads (input order preserved, unknown ids skipped) ──────────────────────────

    List<Track> findTracksByIds(Collection<UUID> ids);

    List<Album> findAlbumsByIds(Collection<UUID> ids);

    List<Artist> findArtistsByIds(Collection<UUID> ids);

    // ── Search result cache ────────────────────────────────────────────────────────────────

    Optional<CachedSearch> findCachedSearch(String normalizedQuery, SearchType type);

    void saveCachedSearch(String normalizedQuery, SearchType type, List<UUID> entityIds);

    // ── Local full-text search over our own catalog (ranked, hydrated) ─────────────────────

    List<Track> searchTracksLocally(String query, int limit);

    List<Album> searchAlbumsLocally(String query, int limit);

    List<Artist> searchArtistsLocally(String query, int limit);

    // ── Type-ahead over our own catalog (prefix matches first) ─────────────────────────────

    List<Track> suggestTracks(String query, int limit);

    List<Artist> suggestArtists(String query, int limit);
}

package com.aura.catalog.domain.service;

import com.aura.catalog.config.CatalogProperties;
import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.model.SearchResult;
import com.aura.catalog.domain.model.SearchType;
import com.aura.catalog.domain.model.Track;
import com.aura.catalog.domain.port.CatalogStore;
import com.aura.catalog.domain.port.MusicMetadataProvider;
import com.aura.catalog.domain.port.MusicSearchProvider;
import com.aura.catalog.domain.port.ProviderSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Lazy discovery (Project-Info.md §14):
 *
 * <pre>
 *   read ──► local catalog ──(hit, fresh)──► return
 *              │
 *              ├─(hit, stale)──► provider ──► normalize ──► upsert ──► return   (falls back to stale on outage)
 *              │
 *              └─(miss)────────► provider ──► normalize ──► upsert ──► return
 * </pre>
 *
 * Search is provider-backed in v1 and persists everything it sees, so subsequent reads are local.
 * Local full-text search over the cache arrives with the Search slice (Project-Info.md §31).
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CatalogStore store;
    private final MusicMetadataProvider metadata;
    private final MusicSearchProvider search;
    private final CatalogProperties properties;
    private final Clock clock;

    public CatalogService(CatalogStore store,
                          MusicMetadataProvider metadata,
                          MusicSearchProvider search,
                          CatalogProperties properties,
                          Clock clock) {
        this.store = store;
        this.metadata = metadata;
        this.search = search;
        this.properties = properties;
        this.clock = clock;
    }

    // ── Search ─────────────────────────────────────────────────────────────────────────────

    public SearchResult search(String query, Set<SearchType> types, Integer limit) {
        int effectiveLimit = limit == null
                ? properties.defaultSearchLimit()
                : Math.min(Math.max(limit, 1), properties.maxSearchLimit());
        Set<SearchType> effectiveTypes = types == null || types.isEmpty() ? SearchType.ALL : types;

        ProviderSearchResult result = search.search(query, effectiveTypes, effectiveLimit);

        List<Track> tracks = result.tracks().stream().map(store::upsertTrack).toList();
        List<Album> albums = result.albums().stream().map(store::upsertAlbum).toList();
        List<Artist> artists = result.artists().stream().map(store::upsertArtist).toList();

        log.debug("search '{}' -> {} tracks, {} albums, {} artists persisted",
                query, tracks.size(), albums.size(), artists.size());
        return new SearchResult(query, tracks, albums, artists);
    }

    // ── Tracks ─────────────────────────────────────────────────────────────────────────────

    public Track getTrack(UUID id) {
        Track local = store.findTrackById(id)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Track", id));
        return refreshIfStale(local, local.providerSyncedAt(), local.providerReferences(),
                ref -> metadata.getTrack(ref.providerResourceId()).map(store::upsertTrack).orElse(local));
    }

    /** Discovery by provider id (e.g. a pasted TIDAL link). Local first, then provider. */
    public Track getTrackByProviderRef(ProviderReference ref) {
        return store.findTrackByProviderRef(ref)
                .map(local -> refreshIfStale(local, local.providerSyncedAt(), local.providerReferences(),
                        r -> metadata.getTrack(r.providerResourceId()).map(store::upsertTrack).orElse(local)))
                .or(() -> metadata.getTrack(ref.providerResourceId()).map(store::upsertTrack))
                .orElseThrow(() -> new CatalogEntityNotFoundException("Track", ref.provider() + ":" + ref.providerResourceId()));
    }

    public List<Track> findTracksByIsrc(String isrc) {
        List<Track> local = store.findTracksByIsrc(isrc);
        if (!local.isEmpty()) return local;
        return metadata.findTracksByIsrc(isrc).stream().map(store::upsertTrack).toList();
    }

    // ── Albums ─────────────────────────────────────────────────────────────────────────────

    public Album getAlbum(UUID id) {
        Album local = store.findAlbumById(id)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Album", id));
        return refreshIfStale(local, local.providerSyncedAt(), local.providerReferences(),
                ref -> metadata.getAlbum(ref.providerResourceId()).map(store::upsertAlbum).orElse(local));
    }

    public Album getAlbumByProviderRef(ProviderReference ref) {
        return store.findAlbumByProviderRef(ref)
                .or(() -> metadata.getAlbum(ref.providerResourceId()).map(store::upsertAlbum))
                .orElseThrow(() -> new CatalogEntityNotFoundException("Album", ref.provider() + ":" + ref.providerResourceId()));
    }

    // ── Artists ────────────────────────────────────────────────────────────────────────────

    public Artist getArtist(UUID id) {
        Artist local = store.findArtistById(id)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Artist", id));
        return refreshIfStale(local, local.providerSyncedAt(), local.providerReferences(),
                ref -> metadata.getArtist(ref.providerResourceId()).map(store::upsertArtist).orElse(local));
    }

    public Artist getArtistByProviderRef(ProviderReference ref) {
        return store.findArtistByProviderRef(ref)
                .or(() -> metadata.getArtist(ref.providerResourceId()).map(store::upsertArtist))
                .orElseThrow(() -> new CatalogEntityNotFoundException("Artist", ref.provider() + ":" + ref.providerResourceId()));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    private <T> T refreshIfStale(T local, Instant syncedAt, List<ProviderReference> refs,
                                 Function<ProviderReference, T> refresh) {
        if (!isStale(syncedAt)) return local;
        ProviderReference ref = refs.stream()
                .filter(r -> r.provider() == metadata.provider())
                .findFirst()
                .orElse(null);
        if (ref == null) return local;
        return orLocal(local, () -> refresh.apply(ref));
    }

    private boolean isStale(Instant syncedAt) {
        return syncedAt == null || syncedAt.plus(properties.metadataTtl()).isBefore(clock.instant());
    }

    /** Stale-but-present beats unavailable (Project-Info.md §48). */
    private <T> T orLocal(T local, Supplier<T> remote) {
        try {
            return remote.get();
        } catch (ProviderUnavailableException e) {
            log.warn("Provider {} unavailable, serving stale local copy: {}", metadata.provider(), e.getMessage());
            return local;
        }
    }
}

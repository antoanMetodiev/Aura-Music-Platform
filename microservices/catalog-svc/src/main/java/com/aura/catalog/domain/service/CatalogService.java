package com.aura.catalog.domain.service;

import com.aura.catalog.config.AppConfig;
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
import com.aura.catalog.domain.port.ProviderTrack;
import com.aura.catalog.domain.port.UpsertedBatch;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
    private final ExecutorService refreshExecutor;
    private final Cache<String, Hydrated> memory;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> refreshing = new ConcurrentHashMap<>();

    public CatalogService(CatalogStore store,
                          MusicMetadataProvider metadata,
                          MusicSearchProvider search,
                          CatalogProperties properties,
                          Clock clock,
                          @AppConfig.HttpIo ExecutorService httpIoExecutor) {
        this.store = store;
        this.metadata = metadata;
        this.search = search;
        this.properties = properties;
        this.clock = clock;
        this.refreshExecutor = httpIoExecutor;
        this.memory = Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(properties.searchMemoryTtl())
                .build();
    }

    // ── Search ─────────────────────────────────────────────────────────────────────────────

    /**
     * Three tiers, checked per requested type (Project-Info.md §14, §20):
     * <ol>
     *   <li>in-memory (Caffeine) — hydrated results for hot queries, {@code searchMemoryTtl};</li>
     *   <li>Postgres — {@code catalog.search_results} holds the ordered ids a query returned, and the
     *       entities themselves already live in our catalog, so a repeat search never touches TIDAL;</li>
     *   <li>TIDAL — only for a type this query has never been asked for.</li>
     * </ol>
     * A cached list older than {@code searchTtl} is still served immediately and refreshed in the
     * background (stale-while-revalidate), deduplicated so concurrent stale hits trigger one refresh.
     * The provider is asked for exactly the depth requested; a cached list that stops on a page
     * boundary short of a later, deeper request is fetched deeper (see mayHaveMoreThanCached), so
     * the "All" tab's 6 and the Songs tab's 60 share one cache entry that only ever grows.
     */
    public SearchResult search(String query, Set<SearchType> types, Integer limit) {
        int effectiveLimit = limit == null
                ? properties.defaultSearchLimit()
                : Math.min(Math.max(limit, 1), properties.maxSearchLimit());
        Set<SearchType> effectiveTypes = types == null || types.isEmpty() ? SearchType.ALL : types;
        String normalized = normalizeQuery(query);

        Map<SearchType, List<?>> served = new EnumMap<>(SearchType.class);
        Set<SearchType> missing = EnumSet.noneOf(SearchType.class);
        Set<SearchType> stale = EnumSet.noneOf(SearchType.class);
        int deepestCached = effectiveLimit;

        for (SearchType type : effectiveTypes) {
            Hydrated hit = fromMemory(normalized, type);
            if (hit == null) hit = fromDatabase(normalized, type);
            if (hit == null || mayHaveMoreThanCached(hit, effectiveLimit)) {
                missing.add(type);
                continue;
            }
            served.put(type, hit.items());
            if (isExpired(hit.fetchedAt(), properties.searchTtl())) {
                stale.add(type);
                deepestCached = Math.max(deepestCached, hit.items().size());
            }
        }

        if (!missing.isEmpty()) {
            try {
                served.putAll(fetchAndPersist(normalized, query, missing, effectiveLimit));
            } catch (ProviderUnavailableException e) {
                // Whatever our own catalog already knows beats an outage (Project-Info.md §48) —
                // but an empty local answer is not an answer, so that case still surfaces the outage.
                Map<SearchType, List<?>> local = localResults(query, missing, properties.maxSearchLimit());
                if (local.values().stream().allMatch(List::isEmpty)) throw e;
                log.warn("Provider unavailable for search '{}', serving local catalog matches only: {}", query, e.getMessage());
                served.putAll(local);
            }
        }
        if (!stale.isEmpty()) {
            refreshInBackground(normalized, query, stale, deepestCached);
        }

        log.debug("search '{}' -> from provider: {}, stale (refreshing in background): {}", query, missing, stale);
        return new SearchResult(query,
                take(served, SearchType.TRACKS, effectiveLimit),
                take(served, SearchType.ALBUMS, effectiveLimit),
                take(served, SearchType.ARTISTS, effectiveLimit));
    }

    /**
     * Only what our own catalog already knows — never a provider call. The frontend asks for this
     * first and paints it instantly while {@link #search} runs in parallel (Project-Info.md §31).
     */
    public SearchResult searchLocally(String query, Set<SearchType> types, Integer limit) {
        int effectiveLimit = limit == null
                ? properties.defaultSearchLimit()
                : Math.min(Math.max(limit, 1), properties.maxSearchLimit());
        Set<SearchType> effectiveTypes = types == null || types.isEmpty() ? SearchType.ALL : types;
        Map<SearchType, List<?>> local = localResults(query, effectiveTypes, effectiveLimit);
        return new SearchResult(query,
                take(local, SearchType.TRACKS, effectiveLimit),
                take(local, SearchType.ALBUMS, effectiveLimit),
                take(local, SearchType.ARTISTS, effectiveLimit));
    }

    private Map<SearchType, List<?>> localResults(String query, Set<SearchType> types, int limit) {
        String q = query.trim();
        Map<SearchType, List<?>> local = new EnumMap<>(SearchType.class);
        if (types.contains(SearchType.TRACKS)) local.put(SearchType.TRACKS, store.searchTracksLocally(q, limit));
        if (types.contains(SearchType.ALBUMS)) local.put(SearchType.ALBUMS, store.searchAlbumsLocally(q, limit));
        if (types.contains(SearchType.ARTISTS)) local.put(SearchType.ARTISTS, store.searchArtistsLocally(q, limit));
        return local;
    }

    /** A cached list that stopped exactly on a page boundary, short of what is asked for, may have more pages. */
    private boolean mayHaveMoreThanCached(Hydrated hit, int limit) {
        int size = hit.items().size();
        return size > 0 && size < limit && size % properties.searchPageSize() == 0;
    }

    private int roundUpToPage(int limit) {
        int page = properties.searchPageSize();
        return Math.min((limit + page - 1) / page * page, Math.max(properties.maxSearchLimit(), page));
    }

    private Hydrated fromMemory(String normalized, SearchType type) {
        return memory.getIfPresent(memoryKey(normalized, type));
    }

    private Hydrated fromDatabase(String normalized, SearchType type) {
        return store.findCachedSearch(normalized, type).map(cached -> {
            List<?> items = switch (type) {
                case TRACKS -> store.findTracksByIds(cached.entityIds());
                case ALBUMS -> store.findAlbumsByIds(cached.entityIds());
                case ARTISTS -> store.findArtistsByIds(cached.entityIds());
            };
            Hydrated hydrated = new Hydrated(items, cached.fetchedAt());
            memory.put(memoryKey(normalized, type), hydrated);
            return hydrated;
        }).orElse(null);
    }

    /**
     * Asks the provider for {@code types}, persists every entity it returned, caches the id lists.
     * The provider is always asked for whole pages: a list cut short of a page boundary would look
     * "complete" to {@link #mayHaveMoreThanCached} and a later, deeper request would be served short.
     */
    private Map<SearchType, List<?>> fetchAndPersist(String normalized, String rawQuery, Set<SearchType> types, int limit) {
        ProviderSearchResult result = search.search(rawQuery, types, roundUpToPage(limit));

        UpsertedBatch persisted = store.upsertBatch(result.tracks(), result.albums(), result.artists());

        Instant now = clock.instant();
        Map<SearchType, List<?>> fetched = new EnumMap<>(SearchType.class);
        if (types.contains(SearchType.TRACKS)) {
            List<Track> tracks = persisted.tracks();
            remember(normalized, SearchType.TRACKS, tracks, tracks.stream().map(Track::id).toList(), now);
            fetched.put(SearchType.TRACKS, tracks);
        }
        if (types.contains(SearchType.ALBUMS)) {
            List<Album> albums = persisted.albums();
            remember(normalized, SearchType.ALBUMS, albums, albums.stream().map(Album::id).toList(), now);
            fetched.put(SearchType.ALBUMS, albums);
        }
        if (types.contains(SearchType.ARTISTS)) {
            List<Artist> artists = persisted.artists();
            remember(normalized, SearchType.ARTISTS, artists, artists.stream().map(Artist::id).toList(), now);
            fetched.put(SearchType.ARTISTS, artists);
        }
        return fetched;
    }

    private void remember(String normalized, SearchType type, List<?> items, List<UUID> ids, Instant now) {
        store.saveCachedSearch(normalized, type, ids);
        memory.put(memoryKey(normalized, type), new Hydrated(items, now));
    }

    private void refreshInBackground(String normalized, String rawQuery, Set<SearchType> types, int limit) {
        String key = normalized + "|" + types;
        refreshing.computeIfAbsent(key, k -> CompletableFuture.runAsync(() -> {
            try {
                // Stale-while-revalidate is background work too: interactive searches go first at TIDAL.
                com.aura.catalog.adapter.provider.tidal.TidalCallPriority.runAsBackground(() -> fetchAndPersist(normalized, rawQuery, types, limit));
            } catch (RuntimeException e) {
                log.warn("Background refresh of search '{}' {} failed, cached results stay in use: {}", rawQuery, types, e.getMessage());
            } finally {
                refreshing.remove(k);
            }
        }, refreshExecutor));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> take(Map<SearchType, List<?>> served, SearchType type, int limit) {
        List<T> list = (List<T>) served.getOrDefault(type, List.of());
        return list.size() > limit ? List.copyOf(list.subList(0, limit)) : list;
    }

    private static String normalizeQuery(String query) {
        return query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String memoryKey(String normalized, SearchType type) {
        return type.name() + ":" + normalized;
    }

    private boolean isExpired(Instant at, java.time.Duration ttl) {
        return at.plus(ttl).isBefore(clock.instant());
    }

    /** Hydrated results for one (query, type), plus when the provider was last asked. */
    private record Hydrated(List<?> items, Instant fetchedAt) {
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

    /** Local-only walk of the catalog for background consumers; never touches the provider. */
    public List<Track> scanTracks(Instant createdAfter, UUID afterId, int limit) {
        return store.findTracksCreatedAfter(createdAfter, afterId, Math.min(Math.max(limit, 1), 200));
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

    /**
     * The album's full track list, in play order. Pulled from the provider the first time an album
     * is opened (and again once {@code metadataTtl} passes), persisted like any other discovery, and
     * served from our own catalog in between — so every album a user opens grows the catalog by its
     * whole tracklist, not just the tracks a search happened to surface.
     */
    public List<Track> getAlbumTracks(UUID albumId) {
        Album album = store.findAlbumById(albumId)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Album", albumId));
        ProviderReference ref = providerRef(album.providerReferences());
        if (!isStale(album.tracksSyncedAt()) || ref == null) {
            return store.findTracksByAlbumId(albumId);
        }
        try {
            List<ProviderTrack> items = metadata.getAlbumTracks(ref.providerResourceId());
            store.upsertBatch(items, List.of(), List.of());
            store.markAlbumTracksSynced(albumId);
        } catch (ProviderUnavailableException e) {
            List<Track> local = store.findTracksByAlbumId(albumId);
            if (local.isEmpty()) throw e;
            log.warn("Provider {} unavailable, serving the {} album tracks we already have for {}: {}",
                    metadata.provider(), local.size(), albumId, e.getMessage());
            return local;
        }
        return store.findTracksByAlbumId(albumId);
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
        ProviderReference ref = providerRef(refs);
        if (ref == null) return local;
        return orLocal(local, () -> refresh.apply(ref));
    }

    private ProviderReference providerRef(List<ProviderReference> refs) {
        return refs.stream()
                .filter(r -> r.provider() == metadata.provider())
                .findFirst()
                .orElse(null);
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

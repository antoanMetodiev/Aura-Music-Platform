package com.aura.catalog.domain.service;

import com.aura.catalog.config.AppConfig;
import com.aura.catalog.config.CatalogProperties;
import com.aura.catalog.config.DiscographySyncProperties;
import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.model.SearchResult;
import com.aura.catalog.domain.model.SearchType;
import com.aura.catalog.domain.model.Track;
import com.aura.catalog.domain.port.CatalogStore;
import com.aura.catalog.domain.port.DiscographySyncStore;
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
import java.util.Collection;
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
    private final DiscographySyncStore discography;
    private final MusicMetadataProvider metadata;
    private final MusicSearchProvider search;
    private final CatalogProperties properties;
    private final DiscographySyncProperties discographyProperties;
    private final Clock clock;
    private final ExecutorService refreshExecutor;
    private final Cache<String, Hydrated> memory;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> refreshing = new ConcurrentHashMap<>();

    public CatalogService(CatalogStore store,
                          DiscographySyncStore discography,
                          MusicMetadataProvider metadata,
                          MusicSearchProvider search,
                          CatalogProperties properties,
                          DiscographySyncProperties discographyProperties,
                          Clock clock,
                          @AppConfig.HttpIo ExecutorService httpIoExecutor) {
        this.store = store;
        this.discography = discography;
        this.metadata = metadata;
        this.search = search;
        this.properties = properties;
        this.discographyProperties = discographyProperties;
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
                rankArtists(take(served, SearchType.ARTISTS, effectiveLimit)));
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
                rankArtists(take(local, SearchType.ARTISTS, effectiveLimit)));
    }

    /**
     * Type-ahead: local catalog only, never a provider call (it runs on every keystroke). Tracks
     * that are the same recording released several times (singles, compilations, deluxe editions)
     * collapse to one entry — a dropdown showing "Hello" four times helps nobody.
     */
    public SearchResult suggest(String query, int trackLimit, int artistLimit) {
        String q = query.trim();
        List<Track> tracks = dedupeReleases(store.suggestTracks(q, trackLimit * 3), trackLimit);
        List<Artist> artists = dedupeArtists(rankArtists(store.suggestArtists(q, artistLimit * 3)), artistLimit);
        return new SearchResult(q, tracks, List.of(), artists);
    }

    /**
     * Keeps the first (best-ranked) release per recording: the same ISRC, or the same normalized
     * title by the same primary artist (by canonical row, so a duplicate profile of the artist
     * doesn't make the same song look like two — V14).
     */
    private static List<Track> dedupeReleases(List<Track> tracks, int limit) {
        Set<String> seen = new java.util.HashSet<>();
        List<Track> out = new java.util.ArrayList<>();
        for (Track t : tracks) {
            String artist = t.primaryArtist() == null ? "" : t.primaryArtist().canonicalId().toString();
            String byTitle = "t:" + t.title().trim().toLowerCase(Locale.ROOT) + "|" + artist;
            String byIsrc = t.isrc() == null || t.isrc().isBlank() ? null : "i:" + t.isrc().trim().toUpperCase(Locale.ROOT);
            boolean fresh = seen.add(byTitle);
            if (byIsrc != null) fresh = seen.add(byIsrc) && fresh;
            if (fresh) out.add(t);
            if (out.size() == limit) break;
        }
        return out;
    }

    /**
     * Provider duplicates of one artist collapse to the canonical row (V14), and among artists that
     * still share a name the one we know the most tracks for comes first — a label's two-single
     * profile must not outrank the artist's real catalogue just because the provider scored it a
     * notch higher. Positions are only swapped within a same-name group, so the provider's ranking
     * across different artists is kept.
     */
    private List<Artist> rankArtists(List<Artist> artists) {
        if (artists.isEmpty()) return artists;
        // Aliases → canonical rows, keeping first appearance order and dropping duplicates.
        Map<UUID, Artist> canonicals = new java.util.LinkedHashMap<>();
        List<UUID> missing = new java.util.ArrayList<>();
        for (Artist a : artists) {
            if (!a.isAlias()) canonicals.putIfAbsent(a.id(), a);
            else if (!canonicals.containsKey(a.canonicalArtistId())) { canonicals.put(a.canonicalArtistId(), null); missing.add(a.canonicalArtistId()); }
        }
        if (!missing.isEmpty()) store.findArtistsByIds(missing).forEach(a -> canonicals.put(a.id(), a));
        List<Artist> unique = canonicals.values().stream().filter(java.util.Objects::nonNull).toList();

        Map<UUID, Integer> richness = store.countGroupTracksByCanonical(unique.stream().map(Artist::id).toList());
        // Same-name groups: sort each group's members by richness, then write them back into the group's original slots.
        Map<String, List<Integer>> slotsByName = new java.util.LinkedHashMap<>();
        for (int i = 0; i < unique.size(); i++) {
            slotsByName.computeIfAbsent(unique.get(i).name().trim().toLowerCase(Locale.ROOT), k -> new java.util.ArrayList<>()).add(i);
        }
        Artist[] ranked = unique.toArray(new Artist[0]);
        for (List<Integer> slots : slotsByName.values()) {
            if (slots.size() < 2) continue;
            List<Artist> members = slots.stream().map(unique::get)
                    .sorted(java.util.Comparator.<Artist>comparingInt(a -> richness.getOrDefault(a.id(), 0)).reversed()
                            .thenComparing(java.util.Comparator.comparingDouble(Artist::popularity).reversed()))
                    .toList();
            for (int i = 0; i < slots.size(); i++) ranked[slots.get(i)] = members.get(i);
        }
        return java.util.Arrays.stream(ranked).map(this::withBorrowedArtwork).toList();
    }

    /** A canonical row with no picture shows a duplicate profile's — see {@link #canonical(Artist)}. */
    private Artist withBorrowedArtwork(Artist artist) {
        return artist.artwork() != null || artist.isAlias() ? artist : canonical(artist);
    }

    /** Providers list some artists twice (regional duplicates); keep the first per normalized name. */
    private static List<Artist> dedupeArtists(List<Artist> artists, int limit) {
        Set<String> seen = new java.util.HashSet<>();
        List<Artist> out = new java.util.ArrayList<>();
        for (Artist a : artists) {
            if (seen.add(a.name().trim().toLowerCase(Locale.ROOT))) out.add(a);
            if (out.size() == limit) break;
        }
        return out;
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

    /** Same walk, most popular first — for consumers that spend a budget per track and should spend it on what gets played. */
    public List<Track> scanTracksByPopularity(double popularityBelow, UUID afterId, int limit) {
        return store.findTracksByPopularityBelow(popularityBelow, afterId, Math.min(Math.max(limit, 1), 200));
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


    /**
     * Reads through an alias serve the canonical artist (V14): opening any of a provider's duplicate
     * profiles lands on the one row that stands for the act. The caller can tell by the id changing.
     */
    public Artist getArtist(UUID id) {
        return getArtist(id, false);
    }

    /**
     * {@code localOnly} answers from our own catalog and never refreshes from the provider. A single
     * artist page can afford that refresh — it is how a stale row, or one V18 marked for repair, gets
     * its picture back. A caller reading forty artists to build one feed cannot: forty refreshes queue
     * behind the provider throttle and the whole feed times out, which is exactly what happened.
     */
    public Artist getArtist(UUID id, boolean localOnly) {
        Artist local = canonical(store.findArtistById(id)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Artist", id)));
        if (localOnly) return local;
        return refreshIfStale(local, local.providerSyncedAt(), local.providerReferences(),
                ref -> metadata.getArtist(ref.providerResourceId()).map(store::upsertArtist).orElse(local));
    }

    /**
     * The artist's most popular tracks, one entry per recording, gathered across every duplicate
     * profile in the group. Opening the artist also puts them in the sync queue, so a page that is
     * thin on the first visit (only what a search brought in) fills in within seconds.
     */
    public List<Track> getArtistTopTracks(UUID artistId, int limit) {
        return getArtistTopTracks(artistId, limit, false);
    }

    /**
     * {@code localOnly} answers from our own catalog alone — no discography pull, no provider call,
     * no merge. Service-to-service consumers that read many artists in one request (recommendation-svc
     * picking tracks for twenty candidate artists) must never trigger twenty TIDAL discography syncs.
     */
    public List<Track> getArtistTopTracks(UUID artistId, int limit, boolean localOnly) {
        List<UUID> group = requestedGroup(artistId, !localOnly);
        return dedupeReleases(store.findTracksByArtistIds(group, limit * 4), limit);
    }

    /**
     * The canonical artist's id plus its aliases, exactly as the catalog has them right now — and,
     * unless the caller asked for a purely local read, a note to the discography worker that someone
     * is looking at this artist so it does them next (V16).
     *
     * <p>This used to pull the artist's whole discography from the provider right here, inside the
     * request. It made the first open of an artist take minutes — two, measured, while the provider
     * was rate-limiting us — and the page showed loading skeletons for all of it, to then display
     * whatever the failed pull had left behind anyway. A read now never waits for a provider
     * (Project-Info.md §48): it answers with what we have and the catalog fills in behind it, the
     * same shape as search, lyrics and playback resolution.
     */
    private List<UUID> requestedGroup(UUID artistId, boolean requestSync) {
        Artist artist = canonical(store.findArtistById(artistId)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Artist", artistId)));
        List<UUID> group = store.findArtistGroupIds(artist.id());
        if (requestSync) discography.requestSync(group, discographyProperties.refreshAfter());
        return group;
    }

    /** How complete this artist's catalogue is — what the UI polls while the worker fills it in. */
    public DiscographySyncStore.GroupSyncState discographyState(UUID artistId) {
        return discography.stateOf(requestedGroup(artistId, false));
    }

    /** Local-only walk over the canonical artists, most popular first — for background consumers. */
    public List<Artist> scanArtistsByPopularity(double popularityBelow, UUID afterId, int limit) {
        return store.findCanonicalArtistsByPopularityBelow(popularityBelow, afterId, Math.min(Math.max(limit, 1), 200));
    }

    /**
     * Resolves provider-supplied artist <em>names</em> to our own artists — one canonical row per
     * distinct name, names we don't have simply missing from the result. Never a provider call: this
     * is how a taste graph expressed in names (Last.fm) is mapped onto our ids.
     */
    public List<Artist> findArtistsByNames(Collection<String> names) {
        return store.findArtistsByExactNames(names).stream().map(this::canonical).map(this::withBorrowedArtwork).toList();
    }

    /** Albums, EPs and singles credited to the artist (any profile in the group), newest first. */
    public List<Album> getArtistAlbums(UUID artistId) {
        List<UUID> group = requestedGroup(artistId, true);
        // Providers carry regional / clean-vs-explicit duplicates of the same release; keep one per (title, date).
        Set<String> seen = new java.util.HashSet<>();
        return store.findAlbumsByArtistIds(group).stream()
                .filter(al -> seen.add(al.title().trim().toLowerCase(Locale.ROOT) + "|" + al.releaseDate()))
                .toList();
    }

    /**
     * The row that stands for this artist. A canonical row with no picture borrows one from a
     * duplicate profile — the label that split off a single often uploaded the better photo.
     */
    private Artist canonical(Artist artist) {
        Artist canonical = artist.isAlias() ? store.findArtistById(artist.canonicalArtistId()).orElse(artist) : artist;
        if (canonical.artwork() != null) return canonical;
        List<UUID> aliases = store.findArtistGroupIds(canonical.id());
        if (aliases.size() < 2) return canonical;
        return store.findArtistsByIds(aliases.subList(1, aliases.size())).stream()
                .filter(a -> a.artwork() != null)
                .findFirst()
                .map(a -> new Artist(canonical.id(), canonical.name(), a.artwork(), canonical.popularity(), null,
                        canonical.providerReferences(), canonical.providerSyncedAt(), canonical.createdAt(), canonical.updatedAt()))
                .orElse(canonical);
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

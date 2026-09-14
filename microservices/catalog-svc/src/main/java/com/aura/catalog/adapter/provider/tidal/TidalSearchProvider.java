package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.JsonApiDocument;
import com.aura.catalog.adapter.provider.tidal.dto.JsonApiRelationship;
import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResource;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.SearchType;
import com.aura.catalog.domain.port.MusicSearchProvider;
import com.aura.catalog.domain.port.ProviderAlbum;
import com.aura.catalog.domain.port.ProviderArtist;
import com.aura.catalog.domain.port.ProviderSearchResult;
import com.aura.catalog.domain.port.ProviderTrack;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.aura.catalog.config.AppConfig;

import java.util.concurrent.ExecutorService;

import static com.aura.catalog.adapter.provider.tidal.TidalMapper.REL_ALBUMS;
import static com.aura.catalog.adapter.provider.tidal.TidalMapper.REL_ARTISTS;
import static com.aura.catalog.adapter.provider.tidal.TidalMapper.TYPE_ALBUMS;
import static com.aura.catalog.adapter.provider.tidal.TidalMapper.TYPE_ARTISTS;
import static com.aura.catalog.adapter.provider.tidal.TidalMapper.TYPE_TRACKS;

/**
 * {@link MusicSearchProvider} backed by TIDAL's {@code /searchResults} endpoint (Project-Info.md §12, §36).
 *
 * TIDAL's search returns *ordered id lists* per type (relevance order), each entry a thin resource
 * (title-only, no cover art / album artist). We keep the order, then batch-fetch the full records
 * with {@link TidalIncludes} and — for tracks — hydrate their albums the same way
 * {@link TidalMetadataProvider} does, so a search result is exactly as complete as a direct fetch.
 *
 * <b>Concurrency:</b> tracks/albums/artists are three independent top-level result sets — hydrating
 * them used to happen one after another, so total latency was the *sum* of every HTTP call. They now
 * run on separate virtual threads and only the slowest branch determines how long the search takes.
 * Each branch builds its own {@link ResourceIndex} seeded from the same (immutable) search response,
 * so the branches never share mutable state with each other. Within the tracks branch, the
 * album-hydrate and track-artist-hydrate calls are themselves independent once the tracks are known,
 * so those two also run concurrently.
 */
@Component
@ConditionalOnProperty(prefix = "music.providers.tidal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TidalSearchProvider implements MusicSearchProvider {

    private static final String TYPE_SEARCH_RESULTS = "searchResults";

    private final TidalApiClient client;
    private final TidalMapper mapper;
    private final TidalProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public TidalSearchProvider(TidalApiClient client, TidalMapper mapper, TidalProperties properties,
                               ObjectMapper objectMapper, @AppConfig.HttpIo ExecutorService httpIoExecutor) {
        this.client = client;
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = httpIoExecutor;
    }

    @Override
    public Provider provider() {
        return Provider.TIDAL;
    }

    @Override
    public ProviderSearchResult search(String query, Set<SearchType> types, int limit) {
        Set<String> relationships = new LinkedHashSet<>();
        if (types.contains(SearchType.TRACKS)) relationships.add(TYPE_TRACKS);
        if (types.contains(SearchType.ALBUMS)) relationships.add(TYPE_ALBUMS);
        if (types.contains(SearchType.ARTISTS)) relationships.add(TYPE_ARTISTS);
        if (relationships.isEmpty()) return ProviderSearchResult.empty();

        JsonApiDocument searchDoc = client.search(query, relationships);
        JsonApiResource searchResult = seededFrom(searchDoc).ofType(TYPE_SEARCH_RESULTS).stream().findFirst().orElse(null);
        if (searchResult == null) return ProviderSearchResult.empty();

        // Three independent result sets — walk each one's cursor pages and hydrate concurrently
        // instead of one after another.
        CompletableFuture<List<ProviderTrack>> tracksFuture = types.contains(SearchType.TRACKS)
                ? CompletableFuture.supplyAsync(() -> hydrateTracks(searchDoc, collectIds(searchResult, TYPE_TRACKS, limit)), executor)
                : CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<ProviderAlbum>> albumsFuture = types.contains(SearchType.ALBUMS)
                ? CompletableFuture.supplyAsync(() -> hydrateAlbums(searchDoc, collectIds(searchResult, TYPE_ALBUMS, limit)), executor)
                : CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<ProviderArtist>> artistsFuture = types.contains(SearchType.ARTISTS)
                ? CompletableFuture.supplyAsync(() -> hydrateArtists(searchDoc, collectIds(searchResult, TYPE_ARTISTS, limit)), executor)
                : CompletableFuture.completedFuture(List.of());

        CompletableFuture.allOf(tracksFuture, albumsFuture, artistsFuture).join();
        return new ProviderSearchResult(tracksFuture.join(), albumsFuture.join(), artistsFuture.join());
    }

    // ── Hydration (Project-Info.md §20: batch, never one call per result) ────────────────────

    private List<ProviderTrack> hydrateTracks(JsonApiDocument searchDoc, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        ResourceIndex index = seededFrom(searchDoc);

        runBatches(new LinkedHashSet<>(ids), TidalIncludes.TRACK, index, client::tracksByIds).join();

        // Albums and the tracks' own artists are both independent of each other at this point —
        // run them concurrently. (Artists reachable only via an album relation, not any track's own,
        // are skipped here for latency; they still get a photo once their own TTL refresh runs.)
        Set<String> albumIds = TidalHydrator.referencedIds(index, TYPE_TRACKS, REL_ALBUMS);
        Set<String> artistIds = TidalHydrator.referencedIds(index, TYPE_TRACKS, REL_ARTISTS);

        CompletableFuture<Void> albumsDone = runBatches(albumIds, TidalIncludes.ALBUM, index, client::albumsByIds);
        CompletableFuture<Void> artistsDone = runBatches(artistIds, TidalIncludes.ARTIST, index, client::artistsByIds);
        CompletableFuture.allOf(albumsDone, artistsDone).join();

        return ids.stream().map(id -> index.get(TYPE_TRACKS, id)).flatMap(Optional::stream)
                .map(r -> mapper.toTrack(r, index)).toList();
    }

    private List<ProviderAlbum> hydrateAlbums(JsonApiDocument searchDoc, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        ResourceIndex index = seededFrom(searchDoc);

        runBatches(new LinkedHashSet<>(ids), TidalIncludes.ALBUM, index, client::albumsByIds).join();
        Set<String> artistIds = TidalHydrator.referencedIds(index, TYPE_ALBUMS, REL_ARTISTS);
        runBatches(artistIds, TidalIncludes.ARTIST, index, client::artistsByIds).join();

        return ids.stream().map(id -> index.get(TYPE_ALBUMS, id)).flatMap(Optional::stream)
                .map(r -> mapper.toAlbum(r, index)).toList();
    }

    private List<ProviderArtist> hydrateArtists(JsonApiDocument searchDoc, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        ResourceIndex index = seededFrom(searchDoc);

        runBatches(new LinkedHashSet<>(ids), TidalIncludes.ARTIST, index, client::artistsByIds).join();
        return ids.stream().map(id -> index.get(TYPE_ARTISTS, id)).flatMap(Optional::stream)
                .map(r -> mapper.toArtist(r, index)).toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    /** Runs every batch for one id set concurrently and merges results into {@code index} as they land. */
    private CompletableFuture<Void> runBatches(Set<String> ids, Set<String> include, ResourceIndex index,
                                               java.util.function.BiFunction<List<String>, Set<String>, JsonApiDocument> fetch) {
        List<CompletableFuture<Void>> futures = TidalHydrator.chunks(ids, properties.batchSize()).stream()
                .map(batch -> CompletableFuture.runAsync(() -> index.add(fetch.apply(batch, include), objectMapper), executor))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private ResourceIndex seededFrom(JsonApiDocument searchDoc) {
        ResourceIndex index = new ResourceIndex();
        index.add(searchDoc, objectMapper);
        return index;
    }

    /**
     * TIDAL inlines only the first 20 ids of each result set and paginates the rest behind a cursor
     * link — keep following it until we have {@code limit} ids or it runs out. Pages are cursor-chained,
     * so this walk is necessarily sequential; the three types walk theirs concurrently (see search()).
     */
    private List<String> collectIds(JsonApiResource searchResult, String relationship, int limit) {
        List<String> ids = new ArrayList<>();
        JsonApiRelationship rel = searchResult.relationships() == null ? null : searchResult.relationships().get(relationship);
        if (rel == null) return ids;
        rel.ids().forEach(ref -> ids.add(ref.id()));
        Optional<String> next = rel.nextLink();
        while (ids.size() < limit && next.isPresent()) {
            JsonApiDocument page = client.page(next.get());
            page.dataIds().forEach(ref -> ids.add(ref.id()));
            next = page.nextLink();
        }
        return ids.size() > limit ? List.copyOf(ids.subList(0, limit)) : ids;
    }
}

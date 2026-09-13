package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.JsonApiDocument;
import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResource;
import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResourceId;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
 */
@Component
@ConditionalOnProperty(prefix = "music.providers.tidal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TidalSearchProvider implements MusicSearchProvider {

    private static final String TYPE_SEARCH_RESULTS = "searchResults";

    private final TidalApiClient client;
    private final TidalMapper mapper;
    private final TidalProperties properties;
    private final ObjectMapper objectMapper;

    public TidalSearchProvider(TidalApiClient client, TidalMapper mapper, TidalProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
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

        ResourceIndex index = new ResourceIndex();
        index.add(client.search(query, relationships), objectMapper);

        JsonApiResource searchResult = index.ofType(TYPE_SEARCH_RESULTS).stream().findFirst().orElse(null);
        if (searchResult == null) return ProviderSearchResult.empty();

        List<ProviderTrack> tracks = types.contains(SearchType.TRACKS)
                ? hydrateTracks(index, orderedIds(searchResult, TYPE_TRACKS, limit))
                : List.of();
        List<ProviderAlbum> albums = types.contains(SearchType.ALBUMS)
                ? hydrateAlbums(index, orderedIds(searchResult, TYPE_ALBUMS, limit))
                : List.of();
        List<ProviderArtist> artists = types.contains(SearchType.ARTISTS)
                ? hydrateArtists(index, orderedIds(searchResult, TYPE_ARTISTS, limit))
                : List.of();

        return new ProviderSearchResult(tracks, albums, artists);
    }

    // ── Hydration (Project-Info.md §20: batch, never one call per result) ────────────────────

    private List<ProviderTrack> hydrateTracks(ResourceIndex index, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        for (List<String> batch : TidalHydrator.chunks(new LinkedHashSet<>(ids), properties.batchSize())) {
            index.add(client.tracksByIds(batch, TidalIncludes.TRACK), objectMapper);
        }
        Set<String> albumIds = TidalHydrator.referencedIds(index, TYPE_TRACKS, REL_ALBUMS);
        for (List<String> batch : TidalHydrator.chunks(albumIds, properties.batchSize())) {
            index.add(client.albumsByIds(batch, TidalIncludes.ALBUM), objectMapper);
        }
        hydrateReferencedArtists(index);
        return ids.stream().map(id -> index.get(TYPE_TRACKS, id)).flatMap(java.util.Optional::stream)
                .map(r -> mapper.toTrack(r, index)).toList();
    }

    private List<ProviderAlbum> hydrateAlbums(ResourceIndex index, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        for (List<String> batch : TidalHydrator.chunks(new LinkedHashSet<>(ids), properties.batchSize())) {
            index.add(client.albumsByIds(batch, TidalIncludes.ALBUM), objectMapper);
        }
        hydrateReferencedArtists(index);
        return ids.stream().map(id -> index.get(TYPE_ALBUMS, id)).flatMap(java.util.Optional::stream)
                .map(r -> mapper.toAlbum(r, index)).toList();
    }

    /**
     * Batch-fetches full artists (profile art) for every artist referenced by a track or album
     * currently in the index — one extra call regardless of how many distinct artists are involved
     * (Project-Info.md §20). Without this, artists discovered only as a track/album relation would
     * never get a photo until their own 7-day TTL happens to expire.
     */
    private void hydrateReferencedArtists(ResourceIndex index) {
        Set<String> artistIds = new LinkedHashSet<>();
        artistIds.addAll(TidalHydrator.referencedIds(index, TYPE_TRACKS, REL_ARTISTS));
        artistIds.addAll(TidalHydrator.referencedIds(index, TYPE_ALBUMS, REL_ARTISTS));
        for (List<String> batch : TidalHydrator.chunks(artistIds, properties.batchSize())) {
            index.add(client.artistsByIds(batch, TidalIncludes.ARTIST), objectMapper);
        }
    }

    private List<ProviderArtist> hydrateArtists(ResourceIndex index, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        for (List<String> batch : TidalHydrator.chunks(new LinkedHashSet<>(ids), properties.batchSize())) {
            index.add(client.artistsByIds(batch, TidalIncludes.ARTIST), objectMapper);
        }
        return ids.stream().map(id -> index.get(TYPE_ARTISTS, id)).flatMap(java.util.Optional::stream)
                .map(r -> mapper.toArtist(r, index)).toList();
    }

    private static List<String> orderedIds(JsonApiResource searchResult, String relationship, int limit) {
        return searchResult.related(relationship).stream()
                .map(JsonApiResourceId::id)
                .limit(limit)
                .toList();
    }
}

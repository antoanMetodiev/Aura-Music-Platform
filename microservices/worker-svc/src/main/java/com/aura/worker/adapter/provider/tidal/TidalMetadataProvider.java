package com.aura.worker.adapter.provider.tidal;

import com.aura.worker.adapter.provider.tidal.dto.JsonApiDocument;
import com.aura.worker.adapter.provider.tidal.dto.JsonApiLinkage;
import com.aura.worker.adapter.provider.tidal.dto.JsonApiResource;
import com.aura.worker.domain.model.Provider;
import com.aura.worker.domain.port.MusicMetadataProvider;
import com.aura.worker.domain.port.ProviderAlbum;
import com.aura.worker.domain.port.ProviderArtist;
import com.aura.worker.domain.port.ProviderTrack;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.aura.worker.config.AppConfig;

import java.util.concurrent.ExecutorService;

import static com.aura.worker.adapter.provider.tidal.TidalMapper.REL_ALBUMS;
import static com.aura.worker.adapter.provider.tidal.TidalMapper.REL_ARTISTS;
import static com.aura.worker.adapter.provider.tidal.TidalMapper.TYPE_ALBUMS;
import static com.aura.worker.adapter.provider.tidal.TidalMapper.TYPE_ARTISTS;
import static com.aura.worker.adapter.provider.tidal.TidalMapper.TYPE_TRACKS;

/**
 * {@link MusicMetadataProvider} backed by the TIDAL Catalogue v2 API (Project-Info.md §12, §15).
 * Everything TIDAL-specific — endpoints, {@code include} lists, JSON:API parsing, the two-call
 * hydration needed for artwork — stays behind this class; the workers that use it
 * only ever see {@code Provider*} records.
 *
 * Disabled entirely via {@code music.providers.tidal.enabled=false} (Project-Info.md §37); with no
 * provider bean present the discography worker does not start.
 */
@Component
@ConditionalOnProperty(prefix = "music.providers.tidal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TidalMetadataProvider implements MusicMetadataProvider {

    private final TidalApiClient client;
    private final TidalMapper mapper;
    private final TidalProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public TidalMetadataProvider(TidalApiClient client, TidalMapper mapper, TidalProperties properties,
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
    public Optional<ProviderTrack> getTrack(String providerResourceId) {
        return client.track(providerResourceId, TidalIncludes.TRACK).map(doc -> {
            ResourceIndex index = indexOf(doc);
            hydrateAlbumsAndArtists(index);
            return mapper.toTrack(requireResource(index, TYPE_TRACKS, providerResourceId), index);
        });
    }

    @Override
    public Optional<ProviderAlbum> getAlbum(String providerResourceId) {
        return client.album(providerResourceId, TidalIncludes.ALBUM).map(doc -> {
            ResourceIndex index = indexOf(doc);
            return mapper.toAlbum(requireResource(index, TYPE_ALBUMS, providerResourceId), index);
        });
    }

    /**
     * Walks every page of the album's {@code items} relationship (cursor-chained, so sequential),
     * keeping only tracks (albums can also list videos), then batch-fetches the full track records
     * plus their albums/artists — the inline {@code include=items} resources carry attributes only,
     * no relationships, so they can't be mapped as-is.
     */
    @Override
    public List<ProviderTrack> getAlbumTracks(String providerResourceId) {
        List<JsonApiLinkage> items = collectTrackLinkages(client.albumItems(providerResourceId), Integer.MAX_VALUE);
        ResourceIndex index = hydrateTracks(items);
        return items.stream()
                .map(l -> index.get(TYPE_TRACKS, l.id().id())
                        .map(r -> mapper.toTrack(r, index).withPosition(l.metaInt("volumeNumber"), l.metaInt("trackNumber"))))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * The artist's tracks as a page needs them: TIDAL's {@code FINGERPRINT} collapse (one entry per
     * distinct recording, not per release) and only the first few pages of it. Glass Animals go from
     * 1 243 rows and ~2 minutes to roughly sixty and a few seconds, which is all a page showing ten
     * tracks was ever going to use. Fetching every release of every track is what used to fill the
     * database with music nobody plays; the rest of an album arrives when its page is opened.
     */
    @Override
    public List<ProviderTrack> getArtistTracks(String providerResourceId) {
        List<JsonApiLinkage> items = collectTrackLinkages(
                client.artistTracks(providerResourceId, COLLAPSE_BY_RECORDING), properties.artistTrackPages());
        ResourceIndex index = hydrateTracks(items);
        return items.stream()
                .map(l -> index.get(TYPE_TRACKS, l.id().id()).map(r -> mapper.toTrack(r, index)))
                .flatMap(Optional::stream)
                .toList();
    }

    /** TIDAL's name for "one entry per distinct recording" rather than one per release. */
    private static final String COLLAPSE_BY_RECORDING = "FINGERPRINT";

    /** Walks the pages of a cursor-chained relationship (necessarily sequential), keeping only track linkages. */
    private List<JsonApiLinkage> collectTrackLinkages(Optional<JsonApiDocument> first, int maxPages) {
        if (first.isEmpty()) return List.of();
        List<JsonApiLinkage> items = new ArrayList<>();
        JsonApiDocument page = first.get();
        for (int pages = 1; ; pages++) {
            page.dataLinkages().stream().filter(l -> TYPE_TRACKS.equals(l.id().type())).forEach(items::add);
            Optional<String> next = page.nextLink();
            if (next.isEmpty() || pages >= maxPages) break;
            page = client.page(next.get());
        }
        return items;
    }

    /**
     * Relationship pages inline tracks with attributes only, no relationships — so the full track
     * records are batch-fetched (concurrently), then their albums/artists on top.
     */
    private ResourceIndex hydrateTracks(List<JsonApiLinkage> items) {
        ResourceIndex index = new ResourceIndex();
        if (items.isEmpty()) return index;
        Set<String> ids = items.stream().map(l -> l.id().id()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<String> batch : TidalHydrator.chunks(ids, properties.batchSize())) {
            futures.add(CompletableFuture.runAsync(() -> index.add(client.tracksByIds(batch, TidalIncludes.TRACK), objectMapper), executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        hydrateAlbumsAndArtists(index);
        return index;
    }

    @Override
    public Optional<ProviderArtist> getArtist(String providerResourceId) {
        return client.artist(providerResourceId, TidalIncludes.ARTIST).map(doc -> {
            ResourceIndex index = indexOf(doc);
            return mapper.toArtist(requireResource(index, TYPE_ARTISTS, providerResourceId), index);
        });
    }

    @Override
    public List<ProviderTrack> findTracksByIsrc(String isrc) {
        JsonApiDocument doc = client.tracksByIsrc(isrc, TidalIncludes.TRACK);
        ResourceIndex index = indexOf(doc);
        hydrateAlbumsAndArtists(index);
        return index.ofType(TYPE_TRACKS).stream().map(r -> mapper.toTrack(r, index)).toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    /**
     * Batch-fetches full albums (cover art + album artist) for every album referenced by a track,
     * and full artists (profile art) for every artist those tracks reference directly — the two run
     * concurrently since neither depends on the other. (An artist reachable only via an album's own
     * relation, not any track's, is skipped here for latency; it still gets a photo once its own
     * 7-day TTL refresh runs — Project-Info.md §14.)
     */
    private void hydrateAlbumsAndArtists(ResourceIndex index) {
        Set<String> albumIds = TidalHydrator.referencedIds(index, TYPE_TRACKS, REL_ALBUMS);
        Set<String> artistIds = TidalHydrator.referencedIds(index, TYPE_TRACKS, REL_ARTISTS);

        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        for (List<String> batch : TidalHydrator.chunks(albumIds, properties.batchSize())) {
            futures.add(CompletableFuture.runAsync(() -> index.add(client.albumsByIds(batch, TidalIncludes.ALBUM), objectMapper), executor));
        }
        for (List<String> batch : TidalHydrator.chunks(artistIds, properties.batchSize())) {
            futures.add(CompletableFuture.runAsync(() -> index.add(client.artistsByIds(batch, TidalIncludes.ARTIST), objectMapper), executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private ResourceIndex indexOf(JsonApiDocument doc) {
        ResourceIndex index = new ResourceIndex();
        index.add(doc, objectMapper);
        return index;
    }

    private static JsonApiResource requireResource(ResourceIndex index, String type, String id) {
        return index.get(type, id)
                .orElseThrow(() -> new IllegalStateException("TIDAL response for " + type + " " + id + " did not include it"));
    }
}

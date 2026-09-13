package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.JsonApiDocument;
import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResource;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.port.MusicMetadataProvider;
import com.aura.catalog.domain.port.ProviderAlbum;
import com.aura.catalog.domain.port.ProviderArtist;
import com.aura.catalog.domain.port.ProviderTrack;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.aura.catalog.adapter.provider.tidal.TidalMapper.REL_ALBUMS;
import static com.aura.catalog.adapter.provider.tidal.TidalMapper.REL_ARTISTS;
import static com.aura.catalog.adapter.provider.tidal.TidalMapper.TYPE_ALBUMS;
import static com.aura.catalog.adapter.provider.tidal.TidalMapper.TYPE_ARTISTS;
import static com.aura.catalog.adapter.provider.tidal.TidalMapper.TYPE_TRACKS;

/**
 * {@link MusicMetadataProvider} backed by the TIDAL Catalogue v2 API (Project-Info.md §12, §15).
 * Everything TIDAL-specific — endpoints, {@code include} lists, JSON:API parsing, the two-call
 * hydration needed for artwork — stays behind this class; {@link com.aura.catalog.domain.service.CatalogService}
 * only ever sees {@code Provider*} records.
 *
 * Disabled entirely via {@code music.providers.tidal.enabled=false} (Project-Info.md §37); with no
 * provider bean present, catalog reads/searches serve local data only.
 */
@Component
@ConditionalOnProperty(prefix = "music.providers.tidal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TidalMetadataProvider implements MusicMetadataProvider {

    private final TidalApiClient client;
    private final TidalMapper mapper;
    private final TidalProperties properties;
    private final ObjectMapper objectMapper;

    public TidalMetadataProvider(TidalApiClient client, TidalMapper mapper, TidalProperties properties, ObjectMapper objectMapper) {
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
     * Batch-fetches full albums (cover art + album artist) for every album referenced by a track in
     * the index, then full artists (profile art) for every artist referenced by those tracks or
     * albums. Two extra calls total, regardless of how many distinct albums/artists are involved
     * (Project-Info.md §20: batch, never one call per result) — so a track with 3 featured artists
     * costs the same as one with a single artist.
     */
    private void hydrateAlbumsAndArtists(ResourceIndex index) {
        Set<String> albumIds = TidalHydrator.referencedIds(index, TYPE_TRACKS, REL_ALBUMS);
        for (List<String> batch : TidalHydrator.chunks(albumIds, properties.batchSize())) {
            index.add(client.albumsByIds(batch, TidalIncludes.ALBUM), objectMapper);
        }

        Set<String> artistIds = new LinkedHashSet<>();
        artistIds.addAll(TidalHydrator.referencedIds(index, TYPE_TRACKS, REL_ARTISTS));
        artistIds.addAll(TidalHydrator.referencedIds(index, TYPE_ALBUMS, REL_ARTISTS));
        for (List<String> batch : TidalHydrator.chunks(artistIds, properties.batchSize())) {
            index.add(client.artistsByIds(batch, TidalIncludes.ARTIST), objectMapper);
        }
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

package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResource;
import com.aura.catalog.adapter.provider.tidal.dto.TidalAlbumAttributes;
import com.aura.catalog.adapter.provider.tidal.dto.TidalArtistAttributes;
import com.aura.catalog.adapter.provider.tidal.dto.TidalArtworkAttributes;
import com.aura.catalog.adapter.provider.tidal.dto.TidalTrackAttributes;
import com.aura.catalog.domain.model.AlbumType;
import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.port.ProviderAlbum;
import com.aura.catalog.domain.port.ProviderArtist;
import com.aura.catalog.domain.port.ProviderTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TIDAL JSON:API resources → provider-agnostic records. The only place that knows TIDAL's field names.
 * Missing related resources (not fetched/included) degrade gracefully to {@code null} rather than failing.
 */
@Component
public class TidalMapper {

    private static final Logger log = LoggerFactory.getLogger(TidalMapper.class);

    static final String TYPE_TRACKS = "tracks";
    static final String TYPE_ALBUMS = "albums";
    static final String TYPE_ARTISTS = "artists";
    static final String TYPE_ARTWORKS = "artworks";

    static final String REL_ALBUMS = "albums";
    static final String REL_ARTISTS = "artists";
    static final String REL_COVER_ART = "coverArt";
    static final String REL_PROFILE_ART = "profileArt";

    /** Target width for the rendition we keep. Big enough for hero views, small enough for lists. */
    private static final int PREFERRED_ARTWORK_WIDTH = 640;

    private final ObjectMapper objectMapper;

    public TidalMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProviderTrack toTrack(JsonApiResource resource, ResourceIndex index) {
        TidalTrackAttributes a = attributes(resource, TidalTrackAttributes.class);

        ProviderAlbum album = resource.related(REL_ALBUMS).stream()
                .map(index::get)
                .flatMap(Optional::stream)
                .findFirst()
                .map(r -> toAlbum(r, index))
                .orElse(null);

        List<ProviderArtist> artists = resource.related(REL_ARTISTS).stream()
                .map(index::get)
                .flatMap(Optional::stream)
                .map(r -> toArtist(r, index))
                .toList();

        if (artists.isEmpty() && album != null && album.artist() != null) {
            artists = List.of(album.artist());
        }

        return new ProviderTrack(
                ref(resource),
                a.title(),
                blankToNull(a.version()),
                parseDurationMs(a.duration()),
                blankToNull(a.isrc()),
                Boolean.TRUE.equals(a.explicit()),
                a.popularity() == null ? 0.0 : a.popularity(),
                album,
                artists,
                null,
                null
        );
    }

    public ProviderAlbum toAlbum(JsonApiResource resource, ResourceIndex index) {
        TidalAlbumAttributes a = attributes(resource, TidalAlbumAttributes.class);

        ProviderArtist artist = resource.related(REL_ARTISTS).stream()
                .map(index::get)
                .flatMap(Optional::stream)
                .findFirst()
                .map(r -> toArtist(r, index))
                .orElse(null);

        Artwork artwork = firstArtwork(resource, REL_COVER_ART, index);

        return new ProviderAlbum(
                ref(resource),
                a.title(),
                parseAlbumType(a.albumType()),
                parseDate(a.releaseDate()),
                artist,
                artwork,
                Boolean.TRUE.equals(a.explicit()),
                a.numberOfItems() == null ? 0 : a.numberOfItems(),
                a.popularity() == null ? 0.0 : a.popularity()
        );
    }

    public ProviderArtist toArtist(JsonApiResource resource, ResourceIndex index) {
        TidalArtistAttributes a = attributes(resource, TidalArtistAttributes.class);
        return new ProviderArtist(
                ref(resource),
                a.name(),
                firstArtwork(resource, REL_PROFILE_ART, index),
                a.popularity() == null ? 0.0 : a.popularity()
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    private Artwork firstArtwork(JsonApiResource owner, String relationship, ResourceIndex index) {
        return owner.related(relationship).stream()
                .map(index::get)
                .flatMap(Optional::stream)
                .map(this::pickRendition)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Smallest rendition at or above the preferred width; otherwise the largest we have. */
    Artwork pickRendition(JsonApiResource artworkResource) {
        TidalArtworkAttributes a = attributes(artworkResource, TidalArtworkAttributes.class);
        if (a.files() == null || a.files().isEmpty()) return null;

        List<TidalArtworkAttributes.File> files = a.files().stream()
                .filter(f -> f.href() != null && f.meta() != null)
                .toList();
        if (files.isEmpty()) return null;

        TidalArtworkAttributes.File chosen = files.stream()
                .filter(f -> f.meta().width() >= PREFERRED_ARTWORK_WIDTH)
                .min(Comparator.comparingInt(f -> f.meta().width()))
                .orElseGet(() -> files.stream().max(Comparator.comparingInt(f -> f.meta().width())).orElseThrow());

        return new Artwork(chosen.href(), chosen.meta().width(), chosen.meta().height());
    }

    private <T> T attributes(JsonApiResource resource, Class<T> type) {
        return objectMapper.treeToValue(resource.attributes(), type);
    }

    private static ProviderReference ref(JsonApiResource resource) {
        return new ProviderReference(Provider.TIDAL, resource.id());
    }

    static long parseDurationMs(String iso8601) {
        if (iso8601 == null || iso8601.isBlank()) return 0;
        try {
            return Duration.parse(iso8601).toMillis();
        } catch (DateTimeParseException e) {
            log.warn("Unparseable TIDAL duration '{}'", iso8601);
            return 0;
        }
    }

    static LocalDate parseDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return null;
        try {
            return LocalDate.parse(isoDate);
        } catch (DateTimeParseException e) {
            log.warn("Unparseable TIDAL release date '{}'", isoDate);
            return null;
        }
    }

    static AlbumType parseAlbumType(String value) {
        if (value == null) return AlbumType.UNKNOWN;
        return switch (value.toUpperCase()) {
            case "ALBUM" -> AlbumType.ALBUM;
            case "EP" -> AlbumType.EP;
            case "SINGLE" -> AlbumType.SINGLE;
            default -> AlbumType.UNKNOWN;
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}

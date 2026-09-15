package com.aura.catalog.adapter.web;

import com.aura.catalog.adapter.web.dto.AlbumResponse;
import com.aura.catalog.adapter.web.dto.ArtistResponse;
import com.aura.catalog.adapter.web.dto.LyricsResponse;
import com.aura.catalog.adapter.web.dto.SearchResponse;
import com.aura.catalog.adapter.web.dto.TrackResponse;
import com.aura.catalog.domain.model.SearchType;
import com.aura.catalog.domain.service.CatalogService;
import com.aura.catalog.domain.service.LyricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public catalog API (Project-Info.md §10 mounts this behind the gateway at the same paths).
 * Every read goes through {@link CatalogService}, which owns the lazy-discovery decision of
 * whether to serve local data or go out to TIDAL — this controller only translates HTTP ⇄ domain.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;
    private final LyricsService lyricsService;
    private final CatalogWebMapper mapper;

    public CatalogController(CatalogService catalogService, LyricsService lyricsService, CatalogWebMapper mapper) {
        this.catalogService = catalogService;
        this.lyricsService = lyricsService;
        this.mapper = mapper;
    }

    /**
     * {@code source=local} answers only from our own catalog (instant, never a provider call) —
     * the frontend fires it alongside the default call and paints whichever lands first.
     */
    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(value = "type", required = false) List<String> types,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "source", required = false) String source
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("'q' must not be blank");
        }
        if (source != null && !source.equals("local") && !source.equals("all")) {
            throw new IllegalArgumentException("Unknown source '" + source + "', expected 'local' or 'all'");
        }
        Set<SearchType> parsedTypes = parseTypes(types);
        return mapper.toResponse("local".equals(source)
                ? catalogService.searchLocally(query, parsedTypes, limit)
                : catalogService.search(query, parsedTypes, limit));
    }

    /** Type-ahead for the search box: local catalog only, tracks + artists, prefix matches first. */
    @GetMapping("/suggest")
    public SearchResponse suggest(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("'q' must not be blank");
        }
        int tracks = limit == null ? 5 : Math.min(Math.max(limit, 1), 20);
        int artists = Math.max(2, tracks / 2);
        return mapper.toResponse(catalogService.suggest(query, tracks, artists));
    }

    @GetMapping("/tracks/{id}")
    public TrackResponse getTrack(@PathVariable UUID id) {
        return mapper.toResponse(catalogService.getTrack(id));
    }

    /** 404 {@code LYRICS_NOT_FOUND} when no provider has text for the track; 502 {@code PROVIDER_UNAVAILABLE} on an outage (nothing cached). */
    @GetMapping("/tracks/{id}/lyrics")
    public LyricsResponse getTrackLyrics(@PathVariable UUID id) {
        return mapper.toResponse(lyricsService.getLyrics(id));
    }

    /**
     * Keyset walk over the whole catalog (service-to-service, for workers that need to visit every
     * track). Returns up to {@code limit} tracks strictly after the cursor. Two orders:
     * <ul>
     *   <li>default — insertion order; continue from the last item's {@code createdAt}/{@code id};</li>
     *   <li>{@code order=popularity} — most popular first; continue from the last item's
     *       {@code popularity}/{@code id} via {@code popularityBelow}/{@code afterId}.</li>
     * </ul>
     */
    @GetMapping("/tracks/scan")
    public List<TrackResponse> scanTracks(
            @RequestParam(value = "order", required = false) String order,
            @RequestParam(value = "createdAfter", required = false) java.time.Instant createdAfter,
            @RequestParam(value = "popularityBelow", required = false) Double popularityBelow,
            @RequestParam(value = "afterId", required = false) UUID afterId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if ("popularity".equalsIgnoreCase(order)) {
            // Popularity is 0..1, so anything above 1 with the max UUID means "from the very top".
            return catalogService.scanTracksByPopularity(
                            popularityBelow == null ? 2.0 : popularityBelow,
                            afterId == null ? new UUID(-1L, -1L) : afterId,
                            limit == null ? 50 : limit)
                    .stream().map(mapper::toResponse).toList();
        }
        return catalogService.scanTracks(
                        createdAfter == null ? java.time.Instant.EPOCH : createdAfter,
                        afterId == null ? new UUID(0L, 0L) : afterId,
                        limit == null ? 50 : limit)
                .stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/tracks/by-isrc")
    public List<TrackResponse> getTracksByIsrc(@RequestParam String isrc) {
        if (isrc == null || isrc.isBlank()) {
            throw new IllegalArgumentException("'isrc' must not be blank");
        }
        return catalogService.findTracksByIsrc(isrc).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/albums/{id}")
    public AlbumResponse getAlbum(@PathVariable UUID id) {
        return mapper.toFullResponse(catalogService.getAlbum(id));
    }

    @GetMapping("/albums/{id}/tracks")
    public List<TrackResponse> getAlbumTracks(@PathVariable UUID id) {
        return catalogService.getAlbumTracks(id).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/artists/{id}")
    public ArtistResponse getArtist(@PathVariable UUID id) {
        return mapper.toFullResponse(catalogService.getArtist(id));
    }

    @GetMapping("/artists/{id}/top-tracks")
    public List<TrackResponse> getArtistTopTracks(@PathVariable UUID id,
                                                  @RequestParam(value = "limit", required = false) Integer limit) {
        int effective = limit == null ? 10 : Math.min(Math.max(limit, 1), 50);
        return catalogService.getArtistTopTracks(id, effective).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/artists/{id}/albums")
    public List<AlbumResponse> getArtistAlbums(@PathVariable UUID id) {
        return catalogService.getArtistAlbums(id).stream().map(mapper::toFullResponse).toList();
    }

    private static Set<SearchType> parseTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) return EnumSet.allOf(SearchType.class);
        // Supports both repeated params (?type=TRACKS&type=ALBUMS) and one comma-separated value.
        return raw.stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(CatalogController::parseType)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(SearchType.class)));
    }

    private static SearchType parseType(String value) {
        try {
            return SearchType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown search type '" + value + "', expected one of " + Arrays.toString(SearchType.values()));
        }
    }
}

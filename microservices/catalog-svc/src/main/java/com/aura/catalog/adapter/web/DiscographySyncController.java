package com.aura.catalog.adapter.web;

import com.aura.catalog.config.DiscographySyncProperties;
import com.aura.catalog.domain.port.DiscographySyncStore;
import com.aura.catalog.domain.service.ArtistDiscographyService;
import com.aura.catalog.domain.service.ArtistMergeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Observability for the artist discography sync, from this side of it: the queue, the stats and what
 * was last written. How the fetching itself is going — whether the worker is running, at what pace,
 * what the provider is saying — belongs to worker-svc and is reported there.
 */
@RestController
@RequestMapping("/api/v1/catalog/discovery")
public class DiscographySyncController {

    public record LastOutcome(UUID artistId, String name, int trackCount, int newArtistsDiscovered, String error, Instant at) {
    }

    public record StatusResponse(DiscographySyncStore.SyncStats catalog, String refreshAfter, String retryAfter,
                                 LastOutcome lastOutcome, List<DiscographySyncStore.RecentSync> recent) {
    }

    private final ArtistDiscographyService service;
    private final DiscographySyncProperties properties;
    private final ArtistMergeService artistMerge;

    public DiscographySyncController(ArtistDiscographyService service, DiscographySyncProperties properties,
                                     ArtistMergeService artistMerge) {
        this.service = service;
        this.properties = properties;
        this.artistMerge = artistMerge;
    }

    @GetMapping("/status")
    public StatusResponse status(@RequestParam(value = "recent", required = false) Integer recent) {
        int limit = recent == null ? 20 : Math.min(Math.max(recent, 1), 200);
        LastOutcome last = service.lastOutcome()
                .map(o -> new LastOutcome(o.artistId(), o.name(), o.trackCount(), o.newArtists(), o.error(), o.at()))
                .orElse(null);
        return new StatusResponse(service.stats(), properties.refreshAfter().toString(),
                properties.retryAfter().toString(), last, service.recent(limit));
    }

    /** Folds every same-named artist row that shares a recording or release into one canonical artist (V14). Returns how many rows are aliases now. */
    @GetMapping("/merge-duplicate-artists")
    public java.util.Map<String, Integer> mergeDuplicateArtists() {
        return java.util.Map.of("aliases", artistMerge.mergeAll());
    }
}

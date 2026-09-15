package com.aura.catalog.adapter.web;

import com.aura.catalog.adapter.worker.ArtistDiscographyWorker;
import com.aura.catalog.config.DiscographySyncProperties;
import com.aura.catalog.domain.port.DiscographySyncStore;
import com.aura.catalog.domain.service.ArtistDiscographyService;
import com.aura.catalog.domain.service.ArtistMergeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Observability for the continuous artist discography sync. */
@RestController
@RequestMapping("/api/v1/catalog/discovery")
public class DiscographySyncController {

    public record WorkerStatus(boolean enabled, boolean running, Instant startedAt, long artistsProcessedSinceStart,
                               String delayBetweenArtists, String refreshAfter) {
    }

    public record InProgress(UUID artistId, String name) {
    }

    public record LastOutcome(UUID artistId, String name, int trackCount, int newArtistsDiscovered, String error, Instant at) {
    }

    public record StatusResponse(WorkerStatus worker, DiscographySyncStore.SyncStats catalog, InProgress inProgress,
                                 LastOutcome lastOutcome, List<DiscographySyncStore.RecentSync> recent) {
    }

    private final ArtistDiscographyService service;
    private final DiscographySyncProperties properties;
    private final ObjectProvider<ArtistDiscographyWorker> worker;
    private final ArtistMergeService artistMerge;

    public DiscographySyncController(ArtistDiscographyService service, DiscographySyncProperties properties,
                                     ObjectProvider<ArtistDiscographyWorker> worker, ArtistMergeService artistMerge) {
        this.artistMerge = artistMerge;
        this.service = service;
        this.properties = properties;
        this.worker = worker;
    }

    @GetMapping("/status")
    public StatusResponse status(@RequestParam(value = "recent", required = false) Integer recent) {
        ArtistDiscographyWorker w = worker.getIfAvailable();
        WorkerStatus workerStatus = new WorkerStatus(
                properties.enabled(),
                w != null && w.isRunning(),
                w == null ? null : w.startedAt(),
                w == null ? 0 : w.completed(),
                properties.delayBetweenArtists().toString(),
                properties.refreshAfter().toString());
        InProgress inProgress = service.inProgress().map(a -> new InProgress(a.id(), a.name())).orElse(null);
        LastOutcome last = service.lastOutcome()
                .map(o -> new LastOutcome(o.artist().id(), o.artist().name(), o.trackCount(), o.newArtists(), o.error(), o.at()))
                .orElse(null);
        int limit = recent == null ? 20 : Math.min(Math.max(recent, 1), 200);
        return new StatusResponse(workerStatus, service.stats(), inProgress, last, service.recent(limit));
    }

    /** Runs exactly one sync step right now, on the request thread — handy for checking the pipeline without waiting for the worker. */
    @GetMapping("/sync-next")
    public LastOutcome syncNext() {
        return service.syncNext()
                .map(o -> new LastOutcome(o.artist().id(), o.artist().name(), o.trackCount(), o.newArtists(), o.error(), o.at()))
                .orElse(null);
    }

    /** Folds every same-named artist row that shares a recording or release into one canonical artist (V14). Returns how many rows are aliases now. */
    @GetMapping("/merge-duplicate-artists")
    public java.util.Map<String, Integer> mergeDuplicateArtists() {
        return java.util.Map.of("aliases", artistMerge.mergeAll());
    }
}

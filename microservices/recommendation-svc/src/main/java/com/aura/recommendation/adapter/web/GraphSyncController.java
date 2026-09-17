package com.aura.recommendation.adapter.web;

import com.aura.recommendation.adapter.provider.lastfm.LastFmProperties;
import com.aura.recommendation.adapter.provider.lastfm.LastFmRequestThrottle;
import com.aura.recommendation.adapter.worker.ArtistGraphWorker;
import com.aura.recommendation.config.GraphSyncProperties;
import com.aura.recommendation.domain.port.GraphSyncStore;
import com.aura.recommendation.domain.port.SimilarityGraphStore;
import com.aura.recommendation.domain.service.ArtistGraphService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Observability for the taste-graph build, mirroring the discovery endpoints of the other services. */
@RestController
@RequestMapping("/api/v1/recommendations/discovery")
public class GraphSyncController {

    public record WorkerStatus(boolean enabled, boolean running, boolean providerConfigured, Instant startedAt,
                               long artistsSinceStart, long edgesSinceStart, long pagesSeededSinceStart,
                               double currentRequestsPerSecond, String lastError, String refreshAfter) {
    }

    public record InProgress(UUID artistId, String name) {
    }

    public record StatusResponse(WorkerStatus worker, GraphSyncStore.SyncStats sync, SimilarityGraphStore.GraphStats graph,
                                 long pending, GraphSyncStore.Cursor cursor, InProgress inProgress,
                                 GraphSyncStore.SyncOutcome lastOutcome, List<GraphSyncStore.SyncOutcome> recent) {
    }

    private final ArtistGraphService service;
    private final GraphSyncProperties properties;
    private final LastFmProperties providerProperties;
    private final LastFmRequestThrottle throttle;
    private final ObjectProvider<ArtistGraphWorker> worker;

    public GraphSyncController(ArtistGraphService service, GraphSyncProperties properties,
                               LastFmProperties providerProperties, LastFmRequestThrottle throttle,
                               ObjectProvider<ArtistGraphWorker> worker) {
        this.service = service;
        this.properties = properties;
        this.providerProperties = providerProperties;
        this.throttle = throttle;
        this.worker = worker;
    }

    @GetMapping("/status")
    public StatusResponse status(@RequestParam(value = "recent", required = false) Integer recent) {
        ArtistGraphWorker w = worker.getIfAvailable();
        WorkerStatus workerStatus = new WorkerStatus(
                properties.enabled(),
                w != null && w.isRunning(),
                providerProperties.configured(),
                w == null ? null : w.startedAt(),
                w == null ? 0 : w.artistsSinceStart(),
                w == null ? 0 : w.edgesSinceStart(),
                w == null ? 0 : w.pagesSeededSinceStart(),
                throttle.currentRequestsPerSecond(),
                w == null ? null : w.lastError(),
                properties.refreshAfter().toString());
        int limit = recent == null ? 20 : Math.min(Math.max(recent, 1), 200);
        return new StatusResponse(
                workerStatus,
                service.syncStats(),
                service.graphStats(),
                service.pendingCount(),
                service.cursor().orElse(null),
                service.inProgress().map(a -> new InProgress(a.artistId(), a.name())).orElse(null),
                service.lastOutcome().orElse(null),
                service.recent(limit));
    }

    /** Syncs exactly one artist right now, on the request thread — checks the pipeline without waiting for the worker. */
    @GetMapping("/sync-next")
    public GraphSyncStore.SyncOutcome syncNext() {
        return service.syncNext().orElse(null);
    }

    /** Pulls one more page of artists from catalog-svc into the queue. No provider calls. */
    @GetMapping("/seed-next")
    public ArtistGraphService.SeedOutcome seedNext(@RequestParam(value = "size", required = false) Integer size) {
        return service.seedNextPage(size == null ? properties.seedPageSize() : Math.min(Math.max(size, 1), 1000));
    }

    /**
     * Artists the graph keeps pointing at that our catalogue doesn't have — a wishlist for the catalog,
     * ordered by how often they came up. Importing them costs TIDAL quota, so it stays a manual call.
     */
    @GetMapping("/unresolved")
    public List<SimilarityGraphStore.UnresolvedName> unresolved(@RequestParam(value = "limit", required = false) Integer limit) {
        return service.unresolved(limit == null ? 50 : Math.min(Math.max(limit, 1), 500));
    }
}

package com.aura.recommendation.adapter.web;

import com.aura.recommendation.config.GraphSyncProperties;
import com.aura.recommendation.domain.port.GraphSyncStore;
import com.aura.recommendation.domain.port.SimilarityGraphStore;
import com.aura.recommendation.domain.service.ArtistGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Observability for the taste graph from this side: how much of it exists, what is still queued and
 * what was last written. Whether the fetching is running, at what pace and what Last.fm is saying
 * belongs to worker-svc and is reported there.
 */
@RestController
@RequestMapping("/api/v1/recommendations/discovery")
public class GraphSyncController {

    public record StatusResponse(GraphSyncStore.SyncStats sync, SimilarityGraphStore.GraphStats graph,
                                 long pending, String refreshAfter, GraphSyncStore.Cursor cursor,
                                 GraphSyncStore.SyncOutcome lastOutcome, List<GraphSyncStore.SyncOutcome> recent) {
    }

    private final ArtistGraphService service;
    private final GraphSyncProperties properties;

    public GraphSyncController(ArtistGraphService service, GraphSyncProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/status")
    public StatusResponse status(@RequestParam(value = "recent", required = false) Integer recent) {
        int limit = recent == null ? 20 : Math.min(Math.max(recent, 1), 200);
        return new StatusResponse(
                service.syncStats(),
                service.graphStats(),
                service.pendingCount(),
                properties.refreshAfter().toString(),
                service.cursor().orElse(null),
                service.lastOutcome().orElse(null),
                service.recent(limit));
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

package com.aura.recommendation.adapter.web;

import com.aura.recommendation.adapter.web.dto.GraphWork;
import com.aura.recommendation.config.GraphSyncProperties;
import com.aura.recommendation.domain.port.GraphSyncStore;
import com.aura.recommendation.domain.port.ProviderSimilarArtist;
import com.aura.recommendation.domain.port.ProviderTag;
import com.aura.recommendation.domain.service.ArtistGraphService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The work API worker-svc builds the taste graph through. Service-to-service only — not routed
 * through the gateway.
 *
 * <p>Claim an artist, ask Last.fm (elsewhere, on the worker's own key), post the answer back. When
 * there is nothing to claim the worker asks for a seeding round instead, which walks catalog-svc for
 * artists we haven't queued yet — no provider call in that one, so it is free.
 */
@RestController
@RequestMapping("/api/v1/recommendations/internal/graph")
public class GraphWorkController {

    private final ArtistGraphService service;
    private final GraphSyncProperties properties;

    public GraphWorkController(ArtistGraphService service, GraphSyncProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /** @return the claimed artist, or 204 when every artist's graph is fresh */
    @PostMapping("/claim")
    public ResponseEntity<GraphWork.Claim> claim() {
        return service.claimNext()
                .map(a -> new GraphWork.Claim(a.artistId(), a.name(), a.popularity()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{artistId}")
    public GraphWork.IngestResult ingest(@PathVariable UUID artistId, @RequestBody GraphWork.Ingest body) {
        if (body == null) throw new IllegalArgumentException("A body with 'similar' and 'tags' is required");
        List<ProviderSimilarArtist> similar = body.similar() == null ? List.of() : body.similar().stream()
                .filter(s -> s.name() != null && !s.name().isBlank())
                .map(s -> new ProviderSimilarArtist(s.name(), s.match(), s.mbid(), s.url()))
                .toList();
        List<ProviderTag> tags = body.tags() == null ? List.of() : body.tags().stream()
                .filter(t -> t.name() != null && !t.name().isBlank())
                .map(t -> new ProviderTag(t.name(), t.count()))
                .toList();

        String name = service.nameOf(artistId);
        GraphSyncStore.SyncOutcome outcome = service.ingest(artistId, name, similar, tags);
        return new GraphWork.IngestResult(outcome.artistId(), outcome.name(), outcome.edgeCount(),
                outcome.tagCount(), outcome.unresolvedCount());
    }

    @PostMapping("/{artistId}/release")
    public ResponseEntity<Void> release(@PathVariable UUID artistId, @RequestBody(required = false) GraphWork.Failure body) {
        service.release(artistId, service.nameOf(artistId), body == null ? "provider unavailable" : body.error());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{artistId}/failed")
    public ResponseEntity<Void> failed(@PathVariable UUID artistId, @RequestBody GraphWork.Failure body) {
        service.markFailed(artistId, service.nameOf(artistId), body == null ? "unknown" : body.error());
        return ResponseEntity.noContent().build();
    }

    /** Pulls another page of artists from catalog-svc into the queue. Catalog-only, no provider call. */
    @PostMapping("/seed")
    public GraphWork.SeedResult seed(@RequestParam(value = "size", required = false) Integer size) {
        ArtistGraphService.SeedOutcome outcome = service.seedNextPage(
                size == null ? properties.seedPageSize() : Math.min(Math.max(size, 1), 1000));
        return new GraphWork.SeedResult(outcome.scanned(), outcome.added(), outcome.endOfCatalog());
    }
}

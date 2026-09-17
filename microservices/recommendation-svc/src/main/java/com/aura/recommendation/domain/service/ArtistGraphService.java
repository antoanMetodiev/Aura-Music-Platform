package com.aura.recommendation.domain.service;

import com.aura.recommendation.config.GraphSyncProperties;
import com.aura.recommendation.domain.model.ArtistRef;
import com.aura.recommendation.domain.model.ArtistTag;
import com.aura.recommendation.domain.port.CatalogLookup;
import com.aura.recommendation.domain.port.GraphSyncStore;
import com.aura.recommendation.domain.port.GraphSyncStore.Cursor;
import com.aura.recommendation.domain.port.GraphSyncStore.PendingArtist;
import com.aura.recommendation.domain.port.GraphSyncStore.SyncOutcome;
import com.aura.recommendation.domain.port.ProviderSimilarArtist;
import com.aura.recommendation.domain.port.ProviderTag;
import com.aura.recommendation.domain.port.SimilarityGraphStore;
import com.aura.recommendation.domain.port.SimilarityGraphStore.Edge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The half of the taste-graph build that belongs here: the queue, resolving the provider's artist
 * <em>names</em> to our ids, and writing the edges. The other half — the Last.fm credentials, the
 * pacing and the fetching — lives in worker-svc, which claims an artist, asks the provider, and
 * posts the raw answer back.
 *
 * <p>Resolution deliberately stayed on this side. It needs the catalog and it needs
 * {@link ArtistNameKeys}, and a worker that resolved names itself would be a second place where the
 * rule "which spellings count as the same artist" lives — the kind of duplicated domain logic §51
 * warns about. The worker sends names; we decide what they mean.
 *
 * <p>Outages and "they don't know this artist" stay carefully apart: an empty answer is a result and
 * gets recorded, while an outage releases the claim so the artist is tried again. Recording an outage
 * as "no neighbours" would be permanent damage — nothing would ever ask again.
 */
@Service
public class ArtistGraphService {

    private static final Logger log = LoggerFactory.getLogger(ArtistGraphService.class);

    /** Result of one cheap, catalog-only seeding step. */
    public record SeedOutcome(int scanned, int added, boolean endOfCatalog, Cursor cursor) {
    }

    private final GraphSyncStore sync;
    private final SimilarityGraphStore graph;
    private final CatalogLookup catalog;
    private final GraphSyncProperties properties;
    private final Clock clock;
    private final AtomicReference<SyncOutcome> lastOutcome = new AtomicReference<>();

    public ArtistGraphService(GraphSyncStore sync, SimilarityGraphStore graph, CatalogLookup catalog,
                              GraphSyncProperties properties, Clock clock) {
        this.sync = sync;
        this.graph = graph;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Pulls the next page of canonical artists from catalog-svc into our queue. No provider calls, so
     * it is cheap enough to run whenever the queue runs dry; reaching the end wraps the cursor back to
     * the top, which is how artists the catalog discovered since get picked up.
     */
    public SeedOutcome seedNextPage(int pageSize) {
        Cursor cursor = sync.cursor().orElseGet(() -> Cursor.start(0));
        List<ArtistRef> page = catalog.scanArtists(cursor.popularityBelow(), cursor.afterId(), pageSize);
        if (page.isEmpty()) {
            Cursor restart = Cursor.start(cursor.passes() + 1);
            sync.saveCursor(restart);
            return new SeedOutcome(0, 0, true, restart);
        }
        int added = sync.seed(page);
        ArtistRef last = page.getLast();
        Cursor next = new Cursor(last.popularity(), last.id(), cursor.passes());
        sync.saveCursor(next);
        return new SeedOutcome(page.size(), added, false, next);
    }

    /** Hands out the next artist whose graph is missing or stale, stamping the claim. */
    public Optional<PendingArtist> claimNext() {
        return sync.claimNext(clock.instant().minus(properties.refreshAfter()));
    }

    /**
     * Stores what the provider said about one artist: the neighbours it could name (resolved to our
     * artists) and the tags it carries. Names we don't have are recorded as a wishlist rather than
     * dropped — an artist the graph keeps pointing at is one the catalog is missing.
     */
    public SyncOutcome ingest(UUID artistId, String name, List<ProviderSimilarArtist> similar, List<ProviderTag> tags) {
        Resolution resolution = resolve(artistId, similar);
        graph.replaceEdges(artistId, resolution.edges());
        graph.replaceTags(artistId, tags.stream().map(t -> new ArtistTag(t.name(), t.count())).toList());
        if (!resolution.unresolvedNames().isEmpty()) {
            graph.recordUnresolved(resolution.unresolvedNames());
        }
        sync.markSynced(artistId, resolution.edges().size(), tags.size(), resolution.unresolvedNames().size());

        SyncOutcome outcome = new SyncOutcome(artistId, name, resolution.edges().size(), tags.size(),
                resolution.unresolvedNames().size(), null, clock.instant());
        lastOutcome.set(outcome);
        log.debug("Graph synced for '{}': {} edges, {} tags, {} names we don't have",
                name, resolution.edges().size(), tags.size(), resolution.unresolvedNames().size());
        return outcome;
    }

    /** The provider was unreachable — give the claim back, nothing was learnt about this artist. */
    public void release(UUID artistId, String name, String reason) {
        sync.release(artistId);
        lastOutcome.set(new SyncOutcome(artistId, name, 0, 0, 0, reason, clock.instant()));
        log.warn("Graph sync of '{}' postponed, provider unavailable: {}", name, reason);
    }

    public void markFailed(UUID artistId, String name, String error) {
        sync.markFailed(artistId, error);
        lastOutcome.set(new SyncOutcome(artistId, name, 0, 0, 0, error, clock.instant()));
        log.warn("Graph sync of '{}' failed: {}", name, error);
    }

    /**
     * Provider names to edges. Several of the provider's names can resolve to the same artist of ours
     * (their duplicate profiles, our V14 canonical) — those collapse to one edge at the best score,
     * and a name resolving back to the seed itself is dropped (an artist is not similar to themselves).
     */
    private Resolution resolve(UUID sourceArtistId, List<ProviderSimilarArtist> similar) {
        if (similar.isEmpty()) return new Resolution(List.of(), List.of());

        // Ask for every spelling at once — the names as given plus their loosened forms — so the whole
        // resolution is still one call to catalog-svc however many variants each name has.
        LinkedHashSet<String> lookups = new LinkedHashSet<>();
        similar.forEach(candidate -> lookups.addAll(ArtistNameKeys.of(candidate.name())));
        Map<String, ArtistRef> byKey = new LinkedHashMap<>();
        for (ArtistRef resolved : catalog.findArtistsByNames(lookups)) {
            // Index the artist under its own keys as well: catalog-svc answered our loosened spelling
            // with its own, which is the one that has to be matched back.
            for (String key : ArtistNameKeys.of(resolved.name())) {
                byKey.putIfAbsent(key, resolved);
            }
        }

        Map<UUID, Edge> edges = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        int position = 0;
        for (ProviderSimilarArtist candidate : similar) {
            ArtistRef resolved = ArtistNameKeys.of(candidate.name()).stream()
                    .map(byKey::get)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (resolved == null) {
                unresolved.add(candidate.name());
                continue;
            }
            if (resolved.id().equals(sourceArtistId)) continue;
            Edge edge = new Edge(sourceArtistId, resolved.id(), candidate.match(), position++);
            edges.merge(resolved.id(), edge, (a, b) -> a.score() >= b.score() ? a : b);
        }
        return new Resolution(List.copyOf(edges.values()), unresolved);
    }

    /** The artist's name as we hold it — the work API takes the caller's word for nothing but the id. */
    public String nameOf(UUID artistId) {
        return catalog.findArtist(artistId)
                .map(ArtistRef::name)
                .orElseThrow(() -> new SeedNotFoundException("Artist", artistId));
    }

    public long pendingCount() {
        return sync.pendingCount(clock.instant().minus(properties.refreshAfter()));
    }

    public Optional<SyncOutcome> lastOutcome() {
        return Optional.ofNullable(lastOutcome.get());
    }

    public GraphSyncStore.SyncStats syncStats() {
        return sync.stats();
    }

    public SimilarityGraphStore.GraphStats graphStats() {
        return graph.stats();
    }

    public List<SyncOutcome> recent(int limit) {
        return sync.recent(limit);
    }

    public List<SimilarityGraphStore.UnresolvedName> unresolved(int limit) {
        return graph.unresolved(limit);
    }

    public Optional<Cursor> cursor() {
        return sync.cursor();
    }

    private record Resolution(List<Edge> edges, List<String> unresolvedNames) {
    }
}

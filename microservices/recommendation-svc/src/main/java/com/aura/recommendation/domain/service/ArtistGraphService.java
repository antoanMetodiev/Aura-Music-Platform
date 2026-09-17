package com.aura.recommendation.domain.service;

import com.aura.recommendation.config.GraphSyncProperties;
import com.aura.recommendation.domain.model.ArtistRef;
import com.aura.recommendation.domain.model.ArtistTag;
import com.aura.recommendation.domain.port.ArtistSimilarityProvider;
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
 * Builds the taste graph, one artist at a time, in the background — the only place in this service
 * that talks to the provider at all. Two calls per artist ({@code similar}, {@code topTags}) at the
 * throttled pace; everything a user request does afterwards is SQL over what these calls left behind.
 *
 * <p>The hard part is not fetching but <em>resolving</em>: the provider answers in artist names, we
 * work in ids. A name we have becomes an edge; a name we don't goes into
 * {@code unresolved_artist_names}, which is not an error but a wishlist — an artist the graph keeps
 * pointing at and our catalogue is missing. Feeding those back into catalog search costs TIDAL quota,
 * so it stays a deliberate operation rather than something this worker does on its own.
 *
 * <p>Outages and "they don't know this artist" are handled very differently: an empty answer is a
 * result and gets recorded, while an outage propagates so the worker backs off and the artist stays
 * at the head of the queue. Recording an outage as "no neighbours" would be permanent damage —
 * nothing would ever ask again.
 */
@Service
public class ArtistGraphService {

    private static final Logger log = LoggerFactory.getLogger(ArtistGraphService.class);

    /** Result of one cheap, catalog-only seeding step. */
    public record SeedOutcome(int scanned, int added, boolean endOfCatalog, Cursor cursor) {
    }

    private final GraphSyncStore sync;
    private final SimilarityGraphStore graph;
    private final ArtistSimilarityProvider provider;
    private final CatalogLookup catalog;
    private final GraphSyncProperties properties;
    private final Clock clock;
    private final AtomicReference<PendingArtist> inProgress = new AtomicReference<>();
    private final AtomicReference<SyncOutcome> lastOutcome = new AtomicReference<>();

    public ArtistGraphService(GraphSyncStore sync, SimilarityGraphStore graph, ArtistSimilarityProvider provider,
                              CatalogLookup catalog, GraphSyncProperties properties, Clock clock) {
        this.sync = sync;
        this.graph = graph;
        this.provider = provider;
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

    /**
     * Syncs exactly one artist. Empty when every artist's graph is fresh.
     *
     * @throws ProviderUnavailableException the provider is down — nothing is recorded and the artist
     *                                      keeps its place in the queue
     * @throws CatalogUnavailableException  names could not be resolved, same treatment
     */
    public synchronized Optional<SyncOutcome> syncNext() {
        Instant staleBefore = clock.instant().minus(properties.refreshAfter());
        Optional<PendingArtist> next = sync.nextPending(staleBefore);
        if (next.isEmpty()) return Optional.empty();
        PendingArtist artist = next.get();
        inProgress.set(artist);
        try {
            return Optional.of(syncArtist(artist));
        } finally {
            inProgress.set(null);
        }
    }

    private SyncOutcome syncArtist(PendingArtist artist) {
        try {
            // Both provider calls first, then one write: a failure halfway must not leave an artist
            // with fresh edges and stale tags.
            List<ProviderSimilarArtist> similar = provider.similarTo(artist.name(), properties.similarArtistsRequested());
            List<ProviderTag> tags = provider.topTags(artist.name());

            Resolution resolution = resolve(artist.artistId(), similar);
            graph.replaceEdges(artist.artistId(), resolution.edges());
            graph.replaceTags(artist.artistId(), tags.stream().map(t -> new ArtistTag(t.name(), t.count())).toList());
            if (!resolution.unresolvedNames().isEmpty()) {
                graph.recordUnresolved(resolution.unresolvedNames());
            }
            sync.markSynced(artist.artistId(), resolution.edges().size(), tags.size(), resolution.unresolvedNames().size());

            SyncOutcome outcome = new SyncOutcome(artist.artistId(), artist.name(), resolution.edges().size(),
                    tags.size(), resolution.unresolvedNames().size(), null, clock.instant());
            lastOutcome.set(outcome);
            log.debug("Graph synced for '{}': {} edges, {} tags, {} names we don't have",
                    artist.name(), resolution.edges().size(), tags.size(), resolution.unresolvedNames().size());
            return outcome;
        } catch (ProviderUnavailableException | CatalogUnavailableException e) {
            // Not this artist's fault — leave the queue untouched so the same artist is retried.
            throw e;
        } catch (RuntimeException e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            sync.markFailed(artist.artistId(), error);
            SyncOutcome outcome = new SyncOutcome(artist.artistId(), artist.name(), 0, 0, 0, error, clock.instant());
            lastOutcome.set(outcome);
            log.warn("Graph sync failed for '{}'", artist.name(), e);
            return outcome;
        }
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

    public long pendingCount() {
        return sync.pendingCount(clock.instant().minus(properties.refreshAfter()));
    }

    public Optional<PendingArtist> inProgress() {
        return Optional.ofNullable(inProgress.get());
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

package com.aura.catalog.domain.service;

import com.aura.catalog.config.DiscographySyncProperties;
import com.aura.catalog.domain.port.CatalogStore;
import com.aura.catalog.domain.port.DiscographySyncStore;
import com.aura.catalog.domain.port.DiscographySyncStore.PendingArtist;
import com.aura.catalog.domain.port.ProviderTrack;
import com.aura.catalog.domain.port.UpsertedBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The half of the continuous discography discovery that belongs to catalog-svc: the queue, and
 * persisting what comes back. The other half — the provider credentials, the pacing and the actual
 * fetching — lives in worker-svc, which claims an artist here, goes to TIDAL on its own key, and
 * posts the tracks back.
 *
 * <p>It used to be one loop inside this service. Splitting it was not about code structure: the
 * fetching was spending the same TIDAL credentials the user-facing requests needed, so a user
 * opening an artist page waited behind the worker's backlog (measured at two minutes). The two sides
 * now have separate credentials and therefore separate rate-limit budgets.
 *
 * <p>What has not changed is where the data lives: every track brings its album and its featured
 * artists along, and any of those artists we didn't know yet is inserted with no discography — which
 * puts it in this queue for a later pass. The catalog still grows on its own from whatever seeds it has.
 */
@Service
public class ArtistDiscographyService {

    private static final Logger log = LoggerFactory.getLogger(ArtistDiscographyService.class);

    public record Outcome(UUID artistId, String name, int trackCount, int newArtists, String error, Instant at) {
        public boolean succeeded() {
            return error == null;
        }
    }

    private final DiscographySyncStore queue;
    private final CatalogStore store;
    private final DiscographySyncProperties properties;
    private final ArtistMergeService artistMerge;
    private final AtomicReference<Outcome> last = new AtomicReference<>();

    public ArtistDiscographyService(DiscographySyncStore queue, CatalogStore store,
                                    DiscographySyncProperties properties, ArtistMergeService artistMerge) {
        this.queue = queue;
        this.store = store;
        this.properties = properties;
        this.artistMerge = artistMerge;
    }

    /**
     * Hands out the next artist due a sync and stamps the claim, so a second worker (or a retry of
     * the same one) doesn't pick it up again. Empty when there is nothing to do.
     */
    public Optional<PendingArtist> claimNext() {
        return queue.claimNext(properties.refreshAfter(), properties.retryAfter());
    }

    /**
     * Persists a fetched discography: the tracks, everything they drag in with them, and the sync
     * stamp. Same-named artist profiles are merged right after — the tracks just stored are the
     * evidence that they are one act (V14).
     */
    public Outcome ingest(UUID artistId, String name, List<ProviderTrack> tracks) {
        long artistsBefore = queue.stats(properties.refreshAfter()).artistsTotal();
        UpsertedBatch persisted = store.upsertBatch(tracks, List.of(), List.of());
        queue.markSynced(artistId, persisted.tracks().size());
        artistMerge.mergeDuplicatesNamed(name);
        long newArtists = queue.stats(properties.refreshAfter()).artistsTotal() - artistsBefore;

        Outcome outcome = new Outcome(artistId, name, persisted.tracks().size(), (int) newArtists, null, Instant.now());
        log.info("Discography sync: '{}' -> {} tracks, {} new artists discovered", name, outcome.trackCount(), newArtists);
        last.set(outcome);
        return outcome;
    }

    /**
     * The worker couldn't reach the provider. Nothing is known about the artist, so the claim is
     * undone rather than recorded as an outcome — it goes back to the front of the queue.
     */
    public void release(UUID artistId, String name, String reason) {
        queue.release(artistId);
        log.warn("Discography sync of '{}' postponed, provider unavailable: {}", name, reason);
        last.set(new Outcome(artistId, name, 0, 0, reason, Instant.now()));
    }

    /** The fetch itself failed for a reason that is about this artist; it is retried after {@code retryAfter}. */
    public void markFailed(UUID artistId, String name, String error) {
        queue.markFailed(artistId, error);
        log.warn("Discography sync of '{}' failed, will retry after {}: {}", name, properties.retryAfter(), error);
        last.set(new Outcome(artistId, name, 0, 0, error, Instant.now()));
    }

    /** The artist's name as we hold it — the work API takes the caller's word for nothing but the id. */
    public String nameOf(UUID artistId) {
        return store.findArtistById(artistId)
                .map(com.aura.catalog.domain.model.Artist::name)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Artist", artistId));
    }

    public DiscographySyncStore.SyncStats stats() {
        return queue.stats(properties.refreshAfter());
    }

    public List<DiscographySyncStore.RecentSync> recent(int limit) {
        return queue.recent(limit);
    }

    public Optional<Outcome> lastOutcome() {
        return Optional.ofNullable(last.get());
    }
}

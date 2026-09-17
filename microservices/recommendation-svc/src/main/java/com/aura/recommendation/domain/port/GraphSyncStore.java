package com.aura.recommendation.domain.port;

import com.aura.recommendation.domain.model.ArtistRef;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The graph worker's bookkeeping: whose turn it is, how it went, and where the catalog walk is. */
public interface GraphSyncStore {

    record PendingArtist(UUID artistId, String name, double popularity, Instant attemptedAt) {
    }

    record SyncOutcome(UUID artistId, String name, int edgeCount, int tagCount, int unresolvedCount, String error, Instant at) {
    }

    record Cursor(double popularityBelow, UUID afterId, int passes) {
        /** Popularity is 0..1, so "below 2.0, after the maximum UUID" is the very top of the walk. */
        public static Cursor start(int passes) {
            return new Cursor(2.0, new UUID(-1L, -1L), passes);
        }
    }

    record SyncStats(long artistsKnown, long artistsSynced, long artistsPending, long artistsFailed) {
    }

    /** Adds artists the catalog walk found; already-known artists keep their sync state (name/popularity refresh only). */
    int seed(Collection<ArtistRef> artists);

    /**
     * The next artist due: never-attempted first, then those last attempted before {@code staleBefore},
     * most popular first within each. Empty when every artist's graph is fresh.
     */
    Optional<PendingArtist> nextPending(Instant staleBefore);

    long pendingCount(Instant staleBefore);

    void markSynced(UUID artistId, int edgeCount, int tagCount, int unresolvedCount);

    void markFailed(UUID artistId, String error);

    Optional<Cursor> cursor();

    void saveCursor(Cursor cursor);

    SyncStats stats();

    List<SyncOutcome> recent(int limit);
}

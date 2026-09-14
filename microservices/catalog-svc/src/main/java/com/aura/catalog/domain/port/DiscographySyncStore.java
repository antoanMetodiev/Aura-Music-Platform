package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.ProviderReference;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Work queue for the continuous artist discography sync, backed by columns on {@code catalog.artists}. */
public interface DiscographySyncStore {

    record PendingArtist(UUID id, String name, ProviderReference ref) {
    }

    record SyncStats(long artistsTotal, long artistsSynced, long artistsPending, long artistsFailed, long tracksTotal) {
    }

    record RecentSync(UUID artistId, String name, Integer trackCount, Instant syncedAt, Instant attemptedAt, String error) {
    }

    /**
     * Claims the next artist whose discography was never synced, is older than {@code refreshAfter},
     * or whose last attempt failed more than {@code retryAfter} ago — and stamps the attempt so a
     * second worker (or the next tick) doesn't pick the same one.
     */
    Optional<PendingArtist> claimNext(Duration refreshAfter, Duration retryAfter);

    void markSynced(UUID artistId, int trackCount);

    void markFailed(UUID artistId, String error);

    /** Undo a claim without recording an outcome — the provider was unreachable, nothing is known about the artist. */
    void release(UUID artistId);

    SyncStats stats(Duration refreshAfter);

    List<RecentSync> recent(int limit);
}

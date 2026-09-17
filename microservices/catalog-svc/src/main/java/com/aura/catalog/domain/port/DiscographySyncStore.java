package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.ProviderReference;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Work queue for the continuous artist discography sync, backed by columns on {@code catalog.artists}. */
public interface DiscographySyncStore {

    /**
     * How much of an artist to fetch. Decided here rather than by the caller: whoever claims the
     * artist, one somebody is waiting for gets the cheap pull.
     */
    enum Depth {
        /** One entry per distinct recording, first page only — a few provider calls, a few seconds. */
        QUICK,
        /** Every release of every track. What the catalog wants; what nobody should wait for. */
        FULL
    }

    /** Which artists a claim may take. */
    enum Lane {
        /** Only artists somebody has open right now. Empty most of the time, and that is the point. */
        ON_DEMAND,
        /** Anything due, the walk's own order. */
        BULK
    }

    record PendingArtist(UUID id, String name, ProviderReference ref, Depth depth) {
    }

    record SyncStats(long artistsTotal, long artistsSynced, long artistsPending, long artistsFailed, long tracksTotal) {
    }

    record RecentSync(UUID artistId, String name, Integer trackCount, Instant syncedAt, Instant attemptedAt, String error) {
    }

    /**
     * How far the discography sync has got for a whole artist group (a canonical artist and its
     * duplicate profiles — V14), which is what an artist page actually shows.
     *
     * @param artists how many profiles the group has
     * @param synced  how many of them have their discography
     */
    record GroupSyncState(int artists, int synced, Instant requestedAt, Instant lastSyncedAt, String error) {
        /** Nothing more is coming — the page is as complete as it will get. */
        public boolean complete() {
            return artists > 0 && synced == artists;
        }
    }

    /**
     * Marks these artists as wanted now, so the worker does them before the rest of the queue.
     * Called when someone opens an artist page: a read never waits for the provider any more, it
     * only says that this artist matters more than the next one by popularity.
     */
    void requestSync(Collection<UUID> artistIds);

    GroupSyncState stateOf(Collection<UUID> artistIds);

    /**
     * Claims the next artist due — never synced, synced only {@link Depth#QUICK}, older than
     * {@code refreshAfter}, or last attempted more than {@code retryAfter} ago — and stamps the
     * attempt so a second worker (or the next tick) doesn't pick the same one. {@code lane} narrows
     * what is eligible; the depth of the claim is decided here, not by the caller.
     */
    Optional<PendingArtist> claimNext(Duration refreshAfter, Duration retryAfter, Lane lane);

    /** When the artist's discography was last pulled in full; empty if never. */
    Optional<Instant> syncedAt(UUID artistId);

    void markSynced(UUID artistId, int trackCount, Depth depth);

    void markFailed(UUID artistId, String error);

    /** Undo a claim without recording an outcome — the provider was unreachable, nothing is known about the artist. */
    void release(UUID artistId);

    SyncStats stats(Duration refreshAfter);

    List<RecentSync> recent(int limit);
}

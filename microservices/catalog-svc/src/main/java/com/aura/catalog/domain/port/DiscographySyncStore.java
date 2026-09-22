package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.ProviderReference;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Work queue for the on-demand artist discography sync, backed by columns on {@code catalog.artists}.
 *
 * <p>Only artists somebody has opened are ever in it. There used to be a bulk walk over the whole
 * catalog as well — every artist a track dragged in was fetched in full, then every artist <em>their</em>
 * tracks dragged in — and it filled the database with music nobody asked for (400k tracks for a
 * handful of users). The catalog now grows from what people actually search for and open, nothing else.
 */
public interface DiscographySyncStore {

    record PendingArtist(UUID id, String name, ProviderReference ref) {
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
     * Puts these artists in the queue: the ones never synced, and the ones whose sync is older than
     * {@code refreshAfter} (so new releases show up when the artist is opened again). Called when
     * someone opens an artist page — a read never waits for the provider, it only says what is wanted.
     */
    void requestSync(Collection<UUID> artistIds, Duration refreshAfter);

    GroupSyncState stateOf(Collection<UUID> artistIds);

    /**
     * Claims the next requested artist — most recently requested first, skipping any attempted less
     * than {@code retryAfter} ago — and stamps the attempt so a second worker (or the next tick)
     * doesn't pick the same one.
     */
    Optional<PendingArtist> claimNext(Duration retryAfter);

    /** When the artist's discography was last pulled; empty if never. */
    Optional<Instant> syncedAt(UUID artistId);

    void markSynced(UUID artistId, int trackCount);

    void markFailed(UUID artistId, String error);

    /** Undo a claim without recording an outcome — the provider was unreachable, nothing is known about the artist. */
    void release(UUID artistId);

    SyncStats stats(Duration refreshAfter);

    List<RecentSync> recent(int limit);
}

package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.port.DiscographySyncStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DiscographySyncStore} over the {@code discography_*} columns of {@code catalog.artists}.
 * {@code claimNext} is a single {@code UPDATE … WHERE id = (SELECT … FOR UPDATE SKIP LOCKED)} so
 * concurrent workers never claim the same artist.
 */
@Repository
public class ArtistDiscographyRepository implements DiscographySyncStore {

    private final JdbcClient jdbc;

    public ArtistDiscographyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<PendingArtist> claimNext(Duration refreshAfter, Duration retryAfter, Lane lane) {
        return jdbc.sql("""
                        UPDATE catalog.artists a
                        SET discography_attempted_at = now()
                        WHERE a.id = (
                            SELECT c.id FROM catalog.artists c
                            JOIN catalog.artist_provider_refs r ON r.artist_id = c.id AND r.provider = :provider
                            WHERE (c.discography_synced_at IS NULL
                                   OR c.discography_synced_at < now() - CAST(:refreshAfter AS interval)
                                   -- A quick pull unblocked the page; the walk still owes it the rest (V17).
                                   OR c.discography_depth = 'QUICK')
                              AND (c.discography_attempted_at IS NULL OR c.discography_attempted_at < now() - CAST(:retryAfter AS interval))
                              AND (NOT :onDemandOnly OR c.discography_requested_at IS NOT NULL)
                            -- An artist someone has open right now goes first, whatever the walk was
                            -- about to do (V16); the rest keep the original order, which leaves a
                            -- quick-synced artist at the back — it already has something to show.
                            ORDER BY (c.discography_requested_at IS NOT NULL) DESC,
                                     c.discography_requested_at DESC NULLS LAST,
                                     c.discography_attempted_at NULLS FIRST, c.created_at
                            LIMIT 1
                            FOR UPDATE OF c SKIP LOCKED
                        )
                        RETURNING a.id, a.name,
                            -- Somebody is waiting and we have nothing to show them yet: fetch the cheap
                            -- version. Whichever lane won the claim, the answer is the same.
                            CASE WHEN a.discography_requested_at IS NOT NULL AND a.discography_synced_at IS NULL
                                 THEN 'QUICK' ELSE 'FULL' END AS depth,
                            (SELECT provider_resource_id FROM catalog.artist_provider_refs
                             WHERE artist_id = a.id AND provider = :provider) AS provider_resource_id
                        """)
                .param("provider", Provider.TIDAL.name())
                .param("refreshAfter", toInterval(refreshAfter))
                .param("retryAfter", toInterval(retryAfter))
                .param("onDemandOnly", lane == Lane.ON_DEMAND)
                .query((rs, n) -> new PendingArtist((UUID) rs.getObject("id"), rs.getString("name"),
                        new ProviderReference(Provider.TIDAL, rs.getString("provider_resource_id")),
                        Depth.valueOf(rs.getString("depth"))))
                .optional();
    }

    @Override
    public Optional<Instant> syncedAt(UUID artistId) {
        return jdbc.sql("SELECT discography_synced_at FROM catalog.artists WHERE id = :id")
                .param("id", artistId)
                .query((rs, n) -> ArtistRepository.toInstant(rs, "discography_synced_at"))
                .optional()
                .filter(java.util.Objects::nonNull);
    }

    @Override
    public void requestSync(Collection<UUID> artistIds) {
        if (artistIds.isEmpty()) return;
        // Bound as text[] and cast: pgjdbc encodes String[] natively, UUID[] it does not.
        String[] ids = artistIds.stream().map(UUID::toString).toArray(String[]::new);
        jdbc.sql("""
                        UPDATE catalog.artists
                        SET discography_requested_at = now()
                        WHERE id = ANY(CAST(:ids AS uuid[])) AND discography_synced_at IS NULL
                        """)
                .param("ids", ids)
                .update();
    }

    @Override
    public GroupSyncState stateOf(Collection<UUID> artistIds) {
        if (artistIds.isEmpty()) return new GroupSyncState(0, 0, null, null, null);
        String[] ids = artistIds.stream().map(UUID::toString).toArray(String[]::new);
        return jdbc.sql("""
                        SELECT count(*) AS artists,
                               count(*) FILTER (WHERE discography_synced_at IS NOT NULL) AS synced,
                               max(discography_requested_at) AS requested_at,
                               max(discography_synced_at) AS synced_at,
                               max(discography_error) AS error
                        FROM catalog.artists
                        WHERE id = ANY(CAST(:ids AS uuid[]))
                        """)
                .param("ids", ids)
                .query((rs, n) -> new GroupSyncState(rs.getInt("artists"), rs.getInt("synced"),
                        ArtistRepository.toInstant(rs, "requested_at"),
                        ArtistRepository.toInstant(rs, "synced_at"),
                        rs.getString("error")))
                .single();
    }

    @Override
    public void markSynced(UUID artistId, int trackCount, Depth depth) {
        // The request mark is cleared either way: a quick pull is what the person opening the page
        // was waiting for, so it has served its purpose and must not keep jumping the queue.
        jdbc.sql("""
                        UPDATE catalog.artists
                        SET discography_synced_at = now(), discography_error = NULL, discography_track_count = :count,
                            discography_depth = :depth, discography_requested_at = NULL
                        WHERE id = :id
                        """)
                .param("count", trackCount)
                .param("depth", depth.name())
                .param("id", artistId)
                .update();
    }

    @Override
    public void markFailed(UUID artistId, String error) {
        jdbc.sql("UPDATE catalog.artists SET discography_error = :error WHERE id = :id")
                .param("error", error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500)))
                .param("id", artistId)
                .update();
    }

    @Override
    public void release(UUID artistId) {
        jdbc.sql("UPDATE catalog.artists SET discography_attempted_at = NULL WHERE id = :id")
                .param("id", artistId)
                .update();
    }

    @Override
    public SyncStats stats(Duration refreshAfter) {
        return jdbc.sql("""
                        SELECT
                            (SELECT count(*) FROM catalog.artists) AS artists_total,
                            -- "Synced" means fully synced: a quick pull is enough for the page but the
                            -- walk still owes this artist the rest, so it counts as pending (V17).
                            (SELECT count(*) FROM catalog.artists
                             WHERE discography_synced_at >= now() - CAST(:refreshAfter AS interval)
                               AND discography_depth = 'FULL') AS artists_synced,
                            (SELECT count(*) FROM catalog.artists
                             WHERE discography_synced_at IS NULL
                                OR discography_synced_at < now() - CAST(:refreshAfter AS interval)
                                OR discography_depth = 'QUICK') AS artists_pending,
                            (SELECT count(*) FROM catalog.artists WHERE discography_error IS NOT NULL) AS artists_failed,
                            (SELECT count(*) FROM catalog.tracks) AS tracks_total
                        """)
                .param("refreshAfter", toInterval(refreshAfter))
                .query((rs, n) -> new SyncStats(rs.getLong("artists_total"), rs.getLong("artists_synced"),
                        rs.getLong("artists_pending"), rs.getLong("artists_failed"), rs.getLong("tracks_total")))
                .single();
    }

    @Override
    public List<RecentSync> recent(int limit) {
        return jdbc.sql("""
                        SELECT id, name, discography_track_count, discography_synced_at, discography_attempted_at, discography_error
                        FROM catalog.artists
                        WHERE discography_attempted_at IS NOT NULL
                        ORDER BY discography_attempted_at DESC
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, n) -> new RecentSync((UUID) rs.getObject("id"), rs.getString("name"),
                        rs.getObject("discography_track_count", Integer.class),
                        ArtistRepository.toInstant(rs, "discography_synced_at"),
                        ArtistRepository.toInstant(rs, "discography_attempted_at"),
                        rs.getString("discography_error")))
                .list();
    }

    private static String toInterval(Duration d) {
        return d.toSeconds() + " seconds";
    }
}

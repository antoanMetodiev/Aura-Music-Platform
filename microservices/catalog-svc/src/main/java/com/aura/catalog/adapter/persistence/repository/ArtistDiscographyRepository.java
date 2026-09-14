package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.port.DiscographySyncStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
    public Optional<PendingArtist> claimNext(Duration refreshAfter, Duration retryAfter) {
        return jdbc.sql("""
                        UPDATE catalog.artists a
                        SET discography_attempted_at = now()
                        WHERE a.id = (
                            SELECT c.id FROM catalog.artists c
                            JOIN catalog.artist_provider_refs r ON r.artist_id = c.id AND r.provider = :provider
                            WHERE (c.discography_synced_at IS NULL OR c.discography_synced_at < now() - CAST(:refreshAfter AS interval))
                              AND (c.discography_attempted_at IS NULL OR c.discography_attempted_at < now() - CAST(:retryAfter AS interval))
                            ORDER BY c.discography_attempted_at NULLS FIRST, c.created_at
                            LIMIT 1
                            FOR UPDATE OF c SKIP LOCKED
                        )
                        RETURNING a.id, a.name,
                            (SELECT provider_resource_id FROM catalog.artist_provider_refs
                             WHERE artist_id = a.id AND provider = :provider) AS provider_resource_id
                        """)
                .param("provider", Provider.TIDAL.name())
                .param("refreshAfter", toInterval(refreshAfter))
                .param("retryAfter", toInterval(retryAfter))
                .query((rs, n) -> new PendingArtist((UUID) rs.getObject("id"), rs.getString("name"),
                        new ProviderReference(Provider.TIDAL, rs.getString("provider_resource_id"))))
                .optional();
    }

    @Override
    public void markSynced(UUID artistId, int trackCount) {
        jdbc.sql("""
                        UPDATE catalog.artists
                        SET discography_synced_at = now(), discography_error = NULL, discography_track_count = :count
                        WHERE id = :id
                        """)
                .param("count", trackCount)
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
                            (SELECT count(*) FROM catalog.artists
                             WHERE discography_synced_at >= now() - CAST(:refreshAfter AS interval)) AS artists_synced,
                            (SELECT count(*) FROM catalog.artists
                             WHERE discography_synced_at IS NULL OR discography_synced_at < now() - CAST(:refreshAfter AS interval)) AS artists_pending,
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

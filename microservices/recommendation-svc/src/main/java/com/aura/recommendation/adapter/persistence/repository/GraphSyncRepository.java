package com.aura.recommendation.adapter.persistence.repository;

import com.aura.recommendation.domain.model.ArtistRef;
import com.aura.recommendation.domain.port.GraphSyncStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link GraphSyncStore} over {@code recommendation.artist_graph_sync} and {@code catalog_scan_cursor}. */
@Repository
public class GraphSyncRepository implements GraphSyncStore {

    private final JdbcClient jdbc;

    public GraphSyncRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int seed(Collection<ArtistRef> artists) {
        if (artists.isEmpty()) return 0;
        List<ArtistRef> distinct = artists.stream()
                .filter(a -> a.id() != null && a.name() != null)
                .collect(java.util.stream.Collectors.toMap(ArtistRef::id, a -> a, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream().toList();
        if (distinct.isEmpty()) return 0;
        // `xmax = 0` is true only for rows this statement actually inserted, so the count says how
        // many artists are new — the number that matters when watching the walk make progress.
        List<Boolean> inserted = jdbc.sql("""
                        INSERT INTO recommendation.artist_graph_sync (artist_id, name, popularity)
                        SELECT a.id, a.name, a.popularity
                        FROM unnest(CAST(:ids AS uuid[]), CAST(:names AS text[]), CAST(:popularities AS float8[]))
                             AS a(id, name, popularity)
                        ON CONFLICT (artist_id) DO UPDATE
                            SET name = EXCLUDED.name, popularity = EXCLUDED.popularity
                        RETURNING (xmax = 0) AS inserted
                        """)
                .param("ids", distinct.stream().map(a -> a.id().toString()).toArray(String[]::new))
                .param("names", distinct.stream().map(ArtistRef::name).toArray(String[]::new))
                .param("popularities", distinct.stream().map(a -> Double.toString(a.popularity())).toArray(String[]::new))
                .query(Boolean.class)
                .list();
        return (int) inserted.stream().filter(Boolean::booleanValue).count();
    }

    @Override
    public Optional<PendingArtist> nextPending(Instant staleBefore) {
        return jdbc.sql("""
                        SELECT artist_id, name, popularity, attempted_at
                        FROM recommendation.artist_graph_sync
                        WHERE attempted_at IS NULL OR attempted_at < :staleBefore
                        ORDER BY attempted_at NULLS FIRST, popularity DESC
                        LIMIT 1
                        """)
                .param("staleBefore", Timestamp.from(staleBefore))
                .query((rs, n) -> new PendingArtist((UUID) rs.getObject("artist_id"), rs.getString("name"),
                        rs.getDouble("popularity"), instant(rs.getTimestamp("attempted_at"))))
                .optional();
    }

    @Override
    public long pendingCount(Instant staleBefore) {
        return jdbc.sql("""
                        SELECT count(*) FROM recommendation.artist_graph_sync
                        WHERE attempted_at IS NULL OR attempted_at < :staleBefore
                        """)
                .param("staleBefore", Timestamp.from(staleBefore))
                .query(Long.class)
                .single();
    }

    @Override
    public void markSynced(UUID artistId, int edgeCount, int tagCount, int unresolvedCount) {
        jdbc.sql("""
                        UPDATE recommendation.artist_graph_sync
                        SET synced_at = now(), attempted_at = now(), error = NULL,
                            edge_count = :edges, tag_count = :tags, unresolved_count = :unresolved
                        WHERE artist_id = :artist
                        """)
                .param("artist", artistId)
                .param("edges", edgeCount)
                .param("tags", tagCount)
                .param("unresolved", unresolvedCount)
                .update();
    }

    @Override
    public void markFailed(UUID artistId, String error) {
        jdbc.sql("""
                        UPDATE recommendation.artist_graph_sync
                        SET attempted_at = now(), error = :error
                        WHERE artist_id = :artist
                        """)
                .param("artist", artistId)
                .param("error", error == null ? null : error.substring(0, Math.min(error.length(), 500)))
                .update();
    }

    @Override
    public Optional<Cursor> cursor() {
        return jdbc.sql("SELECT popularity_below, after_id, passes FROM recommendation.catalog_scan_cursor WHERE id = 1")
                .query((rs, n) -> new Cursor(rs.getDouble("popularity_below"), (UUID) rs.getObject("after_id"), rs.getInt("passes")))
                .optional();
    }

    @Override
    public void saveCursor(Cursor cursor) {
        jdbc.sql("""
                        INSERT INTO recommendation.catalog_scan_cursor (id, popularity_below, after_id, passes, updated_at)
                        VALUES (1, :popularity, :afterId, :passes, now())
                        ON CONFLICT (id) DO UPDATE SET
                            popularity_below = EXCLUDED.popularity_below, after_id = EXCLUDED.after_id,
                            passes = EXCLUDED.passes, updated_at = now()
                        """)
                .param("popularity", cursor.popularityBelow())
                .param("afterId", cursor.afterId())
                .param("passes", cursor.passes())
                .update();
    }

    @Override
    public SyncStats stats() {
        return jdbc.sql("""
                        SELECT count(*) AS known,
                               count(*) FILTER (WHERE synced_at IS NOT NULL) AS synced,
                               count(*) FILTER (WHERE attempted_at IS NULL) AS pending,
                               count(*) FILTER (WHERE error IS NOT NULL) AS failed
                        FROM recommendation.artist_graph_sync
                        """)
                .query((rs, n) -> new SyncStats(rs.getLong("known"), rs.getLong("synced"),
                        rs.getLong("pending"), rs.getLong("failed")))
                .single();
    }

    @Override
    public List<SyncOutcome> recent(int limit) {
        return jdbc.sql("""
                        SELECT artist_id, name, edge_count, tag_count, unresolved_count, error, attempted_at
                        FROM recommendation.artist_graph_sync
                        WHERE attempted_at IS NOT NULL
                        ORDER BY attempted_at DESC
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, n) -> new SyncOutcome((UUID) rs.getObject("artist_id"), rs.getString("name"),
                        rs.getInt("edge_count"), rs.getInt("tag_count"), rs.getInt("unresolved_count"),
                        rs.getString("error"), instant(rs.getTimestamp("attempted_at"))))
                .list();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

package com.aura.playback.adapter.persistence.repository;

import com.aura.playback.domain.model.MatchMethod;
import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.model.PlaybackSource;
import com.aura.playback.domain.port.PlaybackSourceStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Hand-rolled JDBC access for {@code playback.track_sources}. Unlike catalog-svc's upsert
 * repositories, this one is a single row per (track, provider) with no child rows to cascade into,
 * so a native {@code INSERT ... ON CONFLICT DO UPDATE} is enough — one round trip, no lost-race
 * possible, no need for catalog-svc's insert-then-fallback-to-update dance.
 */
@Repository
public class PlaybackSourceRepository implements PlaybackSourceStore {

    private final JdbcClient jdbc;

    public PlaybackSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PlaybackSource> findByTrackId(UUID trackId, PlaybackProvider provider) {
        return jdbc.sql("SELECT * FROM playback.track_sources WHERE track_id = :trackId AND provider = :provider")
                .param("trackId", trackId)
                .param("provider", provider.name())
                .query(PlaybackSourceRepository::mapRow)
                .optional();
    }

    @Override
    public PlaybackSource upsert(PlaybackSource source) {
        return jdbc.sql("""
                        INSERT INTO playback.track_sources
                            (id, track_id, provider, provider_resource_id, title, channel_id, channel_title,
                             duration_ms, match_score, match_method, is_verified, verified_at, created_at, updated_at)
                        VALUES (:id, :trackId, :provider, :providerResourceId, :title, :channelId, :channelTitle,
                                :durationMs, :matchScore, :matchMethod, :isVerified, :verifiedAt, :createdAt, :updatedAt)
                        ON CONFLICT (track_id, provider) DO UPDATE SET
                            provider_resource_id = EXCLUDED.provider_resource_id,
                            title = EXCLUDED.title,
                            channel_id = EXCLUDED.channel_id,
                            channel_title = EXCLUDED.channel_title,
                            duration_ms = EXCLUDED.duration_ms,
                            match_score = EXCLUDED.match_score,
                            match_method = EXCLUDED.match_method,
                            is_verified = EXCLUDED.is_verified,
                            verified_at = EXCLUDED.verified_at,
                            updated_at = EXCLUDED.updated_at
                        RETURNING *
                        """)
                .param("id", source.id())
                .param("trackId", source.trackId())
                .param("provider", source.provider().name())
                .param("providerResourceId", source.providerResourceId())
                .param("title", source.title())
                .param("channelId", source.channelId())
                .param("channelTitle", source.channelTitle())
                .param("durationMs", source.durationMs())
                .param("matchScore", source.matchScore())
                .param("matchMethod", source.matchMethod().name())
                .param("isVerified", source.verified())
                .param("verifiedAt", toTimestamp(source.verifiedAt()), java.sql.Types.TIMESTAMP)
                .param("createdAt", toTimestamp(source.createdAt()))
                .param("updatedAt", toTimestamp(source.updatedAt()))
                .query(PlaybackSourceRepository::mapRow)
                .single();
    }

    private static PlaybackSource mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PlaybackSource(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("track_id"),
                PlaybackProvider.valueOf(rs.getString("provider")),
                rs.getString("provider_resource_id"),
                rs.getString("title"),
                rs.getString("channel_id"),
                rs.getString("channel_title"),
                rs.getLong("duration_ms"),
                rs.getInt("match_score"),
                MatchMethod.valueOf(rs.getString("match_method")),
                rs.getBoolean("is_verified"),
                toInstant(rs, "verified_at"),
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at")
        );
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        var ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    /** pgjdbc's default {@code setObject} doesn't support {@link Instant} directly — bind as {@link Timestamp}. */
    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

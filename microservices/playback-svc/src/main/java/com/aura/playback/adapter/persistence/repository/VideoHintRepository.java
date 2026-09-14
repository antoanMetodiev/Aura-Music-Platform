package com.aura.playback.adapter.persistence.repository;

import com.aura.playback.domain.port.VideoHintStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link VideoHintStore} over {@code playback.video_hints} and {@code playback.catalog_scan_cursor}. */
@Repository
public class VideoHintRepository implements VideoHintStore {

    private final JdbcClient jdbc;

    public VideoHintRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isChecked(UUID trackId) {
        return jdbc.sql("SELECT 1 FROM playback.video_hints WHERE track_id = :id")
                .param("id", trackId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public void save(Hint hint) {
        jdbc.sql("""
                        INSERT INTO playback.video_hints (track_id, isrc, provider, outcome, youtube_id, match_score, checked_at)
                        VALUES (:trackId, :isrc, 'MUSICBRAINZ', :outcome, :youtubeId, :score, now())
                        ON CONFLICT (track_id) DO UPDATE SET
                            isrc = EXCLUDED.isrc, outcome = EXCLUDED.outcome, youtube_id = EXCLUDED.youtube_id,
                            match_score = EXCLUDED.match_score, checked_at = now()
                        """)
                .param("trackId", hint.trackId())
                .param("isrc", hint.isrc())
                .param("outcome", hint.outcome().name())
                .param("youtubeId", hint.youtubeId())
                .param("score", hint.matchScore())
                .update();
    }

    @Override
    public Optional<Cursor> cursor() {
        return jdbc.sql("SELECT created_after, after_id, passes FROM playback.catalog_scan_cursor WHERE id = 1")
                .query((rs, n) -> new Cursor(rs.getTimestamp("created_after").toInstant(),
                        (UUID) rs.getObject("after_id"), rs.getInt("passes")))
                .optional();
    }

    @Override
    public void saveCursor(Cursor cursor) {
        jdbc.sql("""
                        INSERT INTO playback.catalog_scan_cursor (id, created_after, after_id, passes, updated_at)
                        VALUES (1, :after, :afterId, :passes, now())
                        ON CONFLICT (id) DO UPDATE SET
                            created_after = EXCLUDED.created_after, after_id = EXCLUDED.after_id,
                            passes = EXCLUDED.passes, updated_at = now()
                        """)
                .param("after", cursor.createdAfter().atOffset(ZoneOffset.UTC))
                .param("afterId", cursor.afterId())
                .param("passes", cursor.passes())
                .update();
    }

    @Override
    public Stats stats() {
        return jdbc.sql("""
                        SELECT count(*) AS checked,
                               count(*) FILTER (WHERE outcome = 'MATCHED')   AS matched,
                               count(*) FILTER (WHERE outcome = 'REJECTED')  AS rejected,
                               count(*) FILTER (WHERE outcome = 'NO_LINK')   AS no_link,
                               count(*) FILTER (WHERE outcome = 'NOT_FOUND') AS not_found,
                               count(*) FILTER (WHERE outcome = 'NO_ISRC')   AS no_isrc
                        FROM playback.video_hints
                        """)
                .query((rs, n) -> new Stats(rs.getLong("checked"), rs.getLong("matched"), rs.getLong("rejected"),
                        rs.getLong("no_link"), rs.getLong("not_found"), rs.getLong("no_isrc")))
                .single();
    }

    @Override
    public List<Hint> recent(int limit) {
        return jdbc.sql("SELECT * FROM playback.video_hints ORDER BY checked_at DESC LIMIT :limit")
                .param("limit", limit)
                .query(VideoHintRepository::mapHint)
                .list();
    }

    private static Hint mapHint(ResultSet rs, int n) throws SQLException {
        Timestamp checked = rs.getTimestamp("checked_at");
        return new Hint((UUID) rs.getObject("track_id"), rs.getString("isrc"),
                HintOutcome.valueOf(rs.getString("outcome")), rs.getString("youtube_id"),
                rs.getObject("match_score", Integer.class), checked == null ? Instant.EPOCH : checked.toInstant());
    }
}

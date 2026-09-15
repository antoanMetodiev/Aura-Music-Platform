package com.aura.playback.adapter.persistence.repository;

import com.aura.playback.domain.port.VideoHintStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link VideoHintStore} over {@code playback.video_hints} and {@code playback.catalog_scan_cursor}.
 * The {@code provider} column holds the asked sources as a comma-separated list in {@link HintSource}
 * order ({@code MUSICBRAINZ,DISCOGS}) — see V11.
 */
@Repository
public class VideoHintRepository implements VideoHintStore {

    private final JdbcClient jdbc;

    public VideoHintRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Hint> findByTrackIds(Collection<UUID> trackIds) {
        if (trackIds.isEmpty()) return Map.of();
        // Bound as text[] and cast: pgjdbc encodes String[] natively, UUID[] it does not.
        String[] ids = trackIds.stream().map(UUID::toString).toArray(String[]::new);
        return jdbc.sql("SELECT * FROM playback.video_hints WHERE track_id = ANY(CAST(:ids AS uuid[]))")
                .param("ids", ids)
                .query(VideoHintRepository::mapHint)
                .list().stream()
                .collect(Collectors.toMap(Hint::trackId, Function.identity()));
    }

    @Override
    public Optional<Hint> findSibling(String isrc, UUID excludingTrackId) {
        return jdbc.sql("""
                        SELECT * FROM playback.video_hints
                        WHERE isrc = :isrc AND track_id <> :self
                        ORDER BY CASE outcome WHEN 'MATCHED' THEN 0 WHEN 'REJECTED' THEN 1 ELSE 2 END,
                                 length(provider) DESC,
                                 checked_at DESC
                        LIMIT 1
                        """)
                .param("isrc", isrc)
                .param("self", excludingTrackId)
                .query(VideoHintRepository::mapHint)
                .optional();
    }

    @Override
    public void save(Hint hint) {
        jdbc.sql("""
                        INSERT INTO playback.video_hints (track_id, isrc, provider, outcome, youtube_id, match_score, checked_at)
                        VALUES (:trackId, :isrc, :sources, :outcome, :youtubeId, :score, now())
                        ON CONFLICT (track_id) DO UPDATE SET
                            isrc = EXCLUDED.isrc, provider = EXCLUDED.provider, outcome = EXCLUDED.outcome,
                            youtube_id = EXCLUDED.youtube_id, match_score = EXCLUDED.match_score, checked_at = now()
                        """)
                .param("trackId", hint.trackId())
                .param("isrc", hint.isrc())
                .param("sources", encode(hint.sources()))
                .param("outcome", hint.outcome().name())
                .param("youtubeId", hint.youtubeId())
                .param("score", hint.matchScore())
                .update();
    }

    @Override
    public Optional<Cursor> cursor() {
        return jdbc.sql("SELECT popularity_below, after_id, passes FROM playback.catalog_scan_cursor WHERE id = 1")
                .query((rs, n) -> new Cursor(rs.getDouble("popularity_below"), (UUID) rs.getObject("after_id"), rs.getInt("passes")))
                .optional();
    }

    @Override
    public void saveCursor(Cursor cursor) {
        jdbc.sql("""
                        INSERT INTO playback.catalog_scan_cursor (id, popularity_below, after_id, passes, updated_at)
                        VALUES (1, :below, :afterId, :passes, now())
                        ON CONFLICT (id) DO UPDATE SET
                            popularity_below = EXCLUDED.popularity_below, after_id = EXCLUDED.after_id,
                            passes = EXCLUDED.passes, updated_at = now()
                        """)
                .param("below", cursor.popularityBelow())
                .param("afterId", cursor.afterId())
                .param("passes", cursor.passes())
                .update();
    }

    /** Discogs is only asked once MusicBrainz has failed, so a Discogs match is one MusicBrainz didn't make. */
    @Override
    public Stats stats() {
        return jdbc.sql("""
                        SELECT count(*) AS checked,
                               count(*) FILTER (WHERE outcome = 'MATCHED')   AS matched,
                               count(*) FILTER (WHERE outcome = 'REJECTED')  AS rejected,
                               count(*) FILTER (WHERE outcome = 'NO_LINK')   AS no_link,
                               count(*) FILTER (WHERE outcome = 'NOT_FOUND') AS not_found,
                               count(*) FILTER (WHERE outcome = 'NO_ISRC')   AS no_isrc,
                               count(*) FILTER (WHERE provider LIKE '%SIBLING%') AS from_siblings,
                               count(*) FILTER (WHERE outcome = 'MATCHED' AND provider LIKE '%DISCOGS%') AS matched_discogs,
                               count(*) FILTER (WHERE outcome = 'MATCHED' AND provider LIKE '%MUSICBRAINZ%'
                                                  AND provider NOT LIKE '%DISCOGS%') AS matched_mb
                        FROM playback.video_hints
                        """)
                .query((rs, n) -> new Stats(rs.getLong("checked"), rs.getLong("matched"), rs.getLong("rejected"),
                        rs.getLong("no_link"), rs.getLong("not_found"), rs.getLong("no_isrc"), rs.getLong("from_siblings"),
                        rs.getLong("matched_mb"), rs.getLong("matched_discogs")))
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
                decode(rs.getString("provider")),
                HintOutcome.valueOf(rs.getString("outcome")), rs.getString("youtube_id"),
                rs.getObject("match_score", Integer.class), checked == null ? Instant.EPOCH : checked.toInstant());
    }

    /** EnumSet iterates in declaration order, so the stored list is always {@code MUSICBRAINZ,DISCOGS,SIBLING}-ordered. */
    private static String encode(Set<HintSource> sources) {
        return sources.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static Set<HintSource> decode(String column) {
        if (column == null || column.isBlank()) return EnumSet.noneOf(HintSource.class);
        return Arrays.stream(column.split(","))
                .map(String::strip)
                .map(HintSource::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(HintSource.class)));
    }
}

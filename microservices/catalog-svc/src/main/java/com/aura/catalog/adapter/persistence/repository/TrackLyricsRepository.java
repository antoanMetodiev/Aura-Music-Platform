package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Lyrics;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.port.LyricsStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code catalog.track_lyrics} — see V11 migration. One row per track; {@code provider = 'NONE'} is a recorded miss. */
@Repository
public class TrackLyricsRepository implements LyricsStore {

    private static final String NONE = "NONE";
    private static final TypeReference<List<StoredLine>> LINES = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public TrackLyricsRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Cached> find(UUID trackId) {
        return jdbc.sql("SELECT * FROM catalog.track_lyrics WHERE track_id = :id")
                .param("id", trackId)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public void save(Lyrics lyrics) {
        String synced = lyrics.hasSynced()
                ? json.writeValueAsString(lyrics.synced().stream().map(l -> new StoredLine(l.timeMs(), l.text())).toList())
                : null;
        upsert(lyrics.trackId(), lyrics.provider().name(), lyrics.instrumental(), synced, lyrics.plain(), lyrics.fetchedAt());
    }

    @Override
    public void saveMiss(UUID trackId, Instant at) {
        upsert(trackId, NONE, false, null, null, at);
    }

    private void upsert(UUID trackId, String provider, boolean instrumental, String synced, String plain, Instant fetchedAt) {
        jdbc.sql("""
                        INSERT INTO catalog.track_lyrics (track_id, provider, instrumental, synced, plain, fetched_at)
                        VALUES (:id, :provider, :instrumental, CAST(:synced AS jsonb), :plain, :fetchedAt)
                        ON CONFLICT (track_id) DO UPDATE SET
                            provider     = EXCLUDED.provider,
                            instrumental = EXCLUDED.instrumental,
                            synced       = EXCLUDED.synced,
                            plain        = EXCLUDED.plain,
                            fetched_at   = EXCLUDED.fetched_at
                        """)
                .param("id", trackId)
                .param("provider", provider)
                .param("instrumental", instrumental)
                .param("synced", synced)
                .param("plain", plain)
                .param("fetchedAt", java.sql.Timestamp.from(fetchedAt))
                .update();
    }

    private Cached mapRow(ResultSet rs, int rowNum) throws SQLException {
        Instant fetchedAt = rs.getTimestamp("fetched_at").toInstant();
        String provider = rs.getString("provider");
        if (NONE.equals(provider)) return new Cached(null, fetchedAt);

        String syncedJson = rs.getString("synced");
        List<Lyrics.SyncedLine> synced = syncedJson == null ? null
                : json.readValue(syncedJson, LINES).stream().map(l -> new Lyrics.SyncedLine(l.t(), l.text())).toList();
        Lyrics lyrics = new Lyrics(
                rs.getObject("track_id", UUID.class),
                Provider.valueOf(provider),
                rs.getBoolean("instrumental"),
                synced,
                rs.getString("plain"),
                fetchedAt
        );
        return new Cached(lyrics, fetchedAt);
    }

    /** Compact on-disk shape of one synced line: {@code {"t": 13900, "text": "..."}}. */
    private record StoredLine(long t, String text) {
    }
}

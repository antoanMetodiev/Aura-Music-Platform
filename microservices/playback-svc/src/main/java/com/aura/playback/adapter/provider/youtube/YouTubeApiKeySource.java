package com.aura.playback.adapter.provider.youtube;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Which YouTube API key to use right now. Enabled rows of {@code playback.youtube_api_keys} win
 * over the env-configured key; with several rows the service rotates through them in
 * {@code priority} order, and {@link #markQuotaExhausted} benches a key until YouTube's daily
 * quota reset (midnight Pacific) so the next call goes out on a different one.
 */
@Component
public class YouTubeApiKeySource {

    private static final Logger log = LoggerFactory.getLogger(YouTubeApiKeySource.class);
    private static final Duration RELOAD_INTERVAL = Duration.ofMinutes(1);
    private static final ZoneId QUOTA_RESET_ZONE = ZoneId.of("America/Los_Angeles");

    public record ApiKey(UUID id, String label, String value) {
        boolean fromDatabase() {
            return id != null;
        }
    }

    private final JdbcClient jdbc;
    private final YouTubeProperties properties;
    private final Clock clock;

    private volatile List<ApiKey> pool = List.of();
    private volatile Instant loadedAt = Instant.EPOCH;
    private volatile int cursor = 0;

    public YouTubeApiKeySource(JdbcClient jdbc, YouTubeProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public ApiKey current() {
        List<ApiKey> active = activePool();
        return active.get(Math.floorMod(cursor, active.size()));
    }

    /** Benches the key until the next quota reset and moves to the next one. Returns true if another key is available. */
    public synchronized boolean markQuotaExhausted(ApiKey exhausted) {
        if (exhausted.fromDatabase()) {
            Instant resetAt = LocalDate.now(clock.withZone(QUOTA_RESET_ZONE)).plusDays(1)
                    .atStartOfDay(QUOTA_RESET_ZONE).toInstant();
            jdbc.sql("UPDATE playback.youtube_api_keys SET quota_exhausted_until = :until WHERE id = :id")
                    .param("until", resetAt)
                    .param("id", exhausted.id())
                    .update();
            loadedAt = Instant.EPOCH;
        }
        List<ApiKey> active = activePool();
        boolean hasAnother = active.size() > 1 || (active.size() == 1 && !active.get(0).equals(exhausted));
        if (hasAnother) {
            cursor++;
            log.warn("YouTube key '{}' quota exhausted, switching to '{}'", exhausted.label(), current().label());
        } else {
            log.warn("YouTube key '{}' quota exhausted and there is no other key to switch to", exhausted.label());
        }
        return hasAnother;
    }

    public void markUsed(ApiKey key) {
        if (!key.fromDatabase()) return;
        jdbc.sql("UPDATE playback.youtube_api_keys SET last_used_at = now() WHERE id = :id")
                .param("id", key.id())
                .update();
    }

    private List<ApiKey> activePool() {
        if (clock.instant().isAfter(loadedAt.plus(RELOAD_INTERVAL))) reload();
        return pool;
    }

    private synchronized void reload() {
        if (!clock.instant().isAfter(loadedAt.plus(RELOAD_INTERVAL))) return;
        record Row(ApiKey key, boolean exhausted) {
        }
        List<Row> enabled = jdbc.sql("""
                        SELECT id, label, api_key,
                               (quota_exhausted_until IS NOT NULL AND quota_exhausted_until > now()) AS exhausted
                        FROM playback.youtube_api_keys
                        WHERE enabled
                        ORDER BY priority, created_at
                        """)
                .query((rs, n) -> new Row(new ApiKey((UUID) rs.getObject("id"), rs.getString("label"), rs.getString("api_key")),
                        rs.getBoolean("exhausted")))
                .list();
        // An exhausted key is still better than no key — only skip it while an alternative exists.
        List<ApiKey> fromDb = enabled.stream().filter(r -> !r.exhausted()).map(Row::key).toList();
        if (fromDb.isEmpty()) fromDb = enabled.stream().map(Row::key).toList();

        if (!fromDb.isEmpty()) {
            pool = fromDb;
        } else if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            pool = List.of(new ApiKey(null, "env", properties.apiKey()));
        } else {
            throw new IllegalStateException("No YouTube API key: add a row to playback.youtube_api_keys or set YOUTUBE_API_KEY");
        }
        loadedAt = clock.instant();
    }
}

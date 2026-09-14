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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Which YouTube API key to use right now. {@code playback.youtube_api_keys} is the source of truth:
 * every call picks one of its enabled, non-exhausted rows <em>at random</em>, so daily quota is
 * spent evenly over however many keys are in there. The env-configured key is only a bootstrap —
 * on the first start it is inserted as a row (label {@code env}) and never read again while the
 * table has keys. {@link #markQuotaExhausted} benches a key until YouTube's daily quota reset
 * (midnight Pacific).
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

    public YouTubeApiKeySource(JdbcClient jdbc, YouTubeProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /** A random member of the active pool — a fresh pick on every call. */
    public ApiKey current() {
        List<ApiKey> active = activePool();
        return active.get(ThreadLocalRandom.current().nextInt(active.size()));
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
        boolean hasAnother = active.stream().anyMatch(k -> !k.equals(exhausted));
        if (hasAnother) {
            log.warn("YouTube key '{}' quota exhausted; {} key(s) remain in rotation", exhausted.label(), active.size());
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
        seedFromEnvIfEmpty();
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

        if (fromDb.isEmpty()) {
            throw new IllegalStateException("No YouTube API key: add a row to playback.youtube_api_keys or set YOUTUBE_API_KEY");
        }
        pool = fromDb;
        loadedAt = clock.instant();
    }

    /** First start: the env key becomes the first row, so the table is the single source of truth from then on. */
    private void seedFromEnvIfEmpty() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) return;
        long count = jdbc.sql("SELECT count(*) FROM playback.youtube_api_keys").query(Long.class).single();
        if (count > 0) return;
        jdbc.sql("INSERT INTO playback.youtube_api_keys (id, label, api_key) VALUES (:id, 'env', :key) ON CONFLICT (api_key) DO NOTHING")
                .param("id", UUID.randomUUID())
                .param("key", properties.apiKey())
                .update();
        log.info("Seeded playback.youtube_api_keys with the YOUTUBE_API_KEY from the environment");
    }
}

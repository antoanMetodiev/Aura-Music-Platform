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
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Which YouTube API key to use right now, and how much of each key's daily quota is left.
 * {@code playback.youtube_api_keys} is the source of truth: every call picks <em>at random</em>
 * among the enabled keys that still have enough quota for it, so the daily budget is spent evenly
 * over however many keys are in there and a spent key is never even tried.
 *
 * <p>Quota accounting follows YouTube's own model — 10 000 units a day per key, {@code search.list}
 * costing 100 and {@code videos.list} 1 — and lives in the table (atomic {@code UPDATE}s), so it is
 * correct across restarts and several instances. The counter is tagged with the Pacific-time date
 * it belongs to: YouTube resets at midnight Pacific, so a counter from an earlier day reads as zero
 * without any reset job.
 *
 * <p>The env-configured key is only a bootstrap — inserted as a row (label {@code env}) on the first
 * start and never read again while the table has keys.
 */
@Component
public class YouTubeApiKeySource {

    private static final Logger log = LoggerFactory.getLogger(YouTubeApiKeySource.class);
    private static final Duration RELOAD_INTERVAL = Duration.ofSeconds(30);
    static final ZoneId QUOTA_RESET_ZONE = ZoneId.of("America/Los_Angeles");

    public record ApiKey(UUID id, String label, String value, int dailyQuotaUnits, int unitsUsedToday) {
        public int unitsRemaining() {
            return Math.max(0, dailyQuotaUnits - unitsUsedToday);
        }
    }

    /** Read-only view for the status endpoint. */
    public record KeyStatus(UUID id, String label, String keyPrefix, boolean enabled, int dailyQuotaUnits, int unitsUsedToday,
                            int unitsRemaining, int searchesRemaining, LocalDate quotaDay, Instant resetsAt,
                            Instant lastUsedAt) {
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

    /**
     * A random enabled key with at least {@code costUnits} of today's quota left.
     *
     * @throws YouTubeApiClient.QuotaExceededException when no key can afford the call today
     */
    public ApiKey pick(int costUnits) {
        List<ApiKey> affordable = activePool().stream().filter(k -> k.unitsRemaining() >= costUnits).toList();
        if (affordable.isEmpty()) {
            throw new YouTubeApiClient.QuotaExceededException(
                    "Every YouTube key's daily quota is spent (need " + costUnits + " units); resets at " + nextReset(), null);
        }
        return affordable.get(ThreadLocalRandom.current().nextInt(affordable.size()));
    }

    /**
     * Records that {@code costUnits} were spent on {@code key} — before the call goes out, since YouTube
     * bills failed calls too. Atomic in the database; a counter from a previous Pacific day restarts.
     */
    public void charge(ApiKey key, int costUnits) {
        jdbc.sql("""
                        UPDATE playback.youtube_api_keys
                        SET units_used_today = CASE WHEN quota_day = :today THEN units_used_today + :cost ELSE :cost END,
                            quota_day = :today,
                            last_used_at = now()
                        WHERE id = :id
                        """)
                .param("today", todayPacific())
                .param("cost", costUnits)
                .param("id", key.id())
                .update();
        loadedAt = Instant.EPOCH; // the next pick sees the new balance
    }

    /**
     * YouTube itself said the key is out of quota (403 quotaExceeded / 429 per-day) — whatever our
     * counter thought, it is spent until the reset. Returns true if another key still has quota.
     */
    public synchronized boolean markQuotaExhausted(ApiKey exhausted) {
        jdbc.sql("""
                        UPDATE playback.youtube_api_keys
                        SET units_used_today = daily_quota_units, quota_day = :today, quota_exhausted_until = :until
                        WHERE id = :id
                        """)
                .param("today", todayPacific())
                .param("until", nextReset().atOffset(ZoneOffset.UTC))
                .param("id", exhausted.id())
                .update();
        loadedAt = Instant.EPOCH;
        boolean hasAnother = activePool().stream().anyMatch(k -> !k.id().equals(exhausted.id()) && k.unitsRemaining() > 0);
        if (hasAnother) {
            log.warn("YouTube key '{}' quota exhausted (reported by YouTube); other keys still have quota", exhausted.label());
        } else {
            log.warn("YouTube key '{}' quota exhausted and no other key has quota left; resets at {}", exhausted.label(), nextReset());
        }
        return hasAnother;
    }

    public List<KeyStatus> status() {
        LocalDate today = todayPacific();
        Instant resetsAt = nextReset();
        return jdbc.sql("""
                        SELECT id, label, left(api_key, 10) AS key_prefix, enabled, daily_quota_units, units_used_today, quota_day, last_used_at
                        FROM playback.youtube_api_keys ORDER BY created_at
                        """)
                .query((rs, n) -> {
                    java.sql.Date day = rs.getDate("quota_day");
                    LocalDate quotaDay = day == null ? null : day.toLocalDate();
                    int used = today.equals(quotaDay) ? rs.getInt("units_used_today") : 0;
                    int daily = rs.getInt("daily_quota_units");
                    int remaining = Math.max(0, daily - used);
                    java.sql.Timestamp last = rs.getTimestamp("last_used_at");
                    return new KeyStatus((UUID) rs.getObject("id"), rs.getString("label"), rs.getString("key_prefix") + "…", rs.getBoolean("enabled"),
                            daily, used, remaining, remaining / YouTubeApiClient.SEARCH_COST_UNITS, quotaDay, resetsAt,
                            last == null ? null : last.toInstant());
                })
                .list();
    }

    // ── Internals ──────────────────────────────────────────────────────────────────────────

    LocalDate todayPacific() {
        return LocalDate.now(clock.withZone(QUOTA_RESET_ZONE));
    }

    Instant nextReset() {
        return todayPacific().plusDays(1).atStartOfDay(QUOTA_RESET_ZONE).toInstant();
    }

    private List<ApiKey> activePool() {
        if (clock.instant().isAfter(loadedAt.plus(RELOAD_INTERVAL))) reload();
        return pool;
    }

    private synchronized void reload() {
        if (!clock.instant().isAfter(loadedAt.plus(RELOAD_INTERVAL))) return;
        seedFromEnvIfEmpty();
        LocalDate today = todayPacific();
        List<ApiKey> enabled = jdbc.sql("""
                        SELECT id, label, api_key, daily_quota_units, units_used_today, quota_day
                        FROM playback.youtube_api_keys
                        WHERE enabled
                        ORDER BY priority, created_at
                        """)
                .query((rs, n) -> {
                    java.sql.Date day = rs.getDate("quota_day");
                    boolean sameDay = day != null && today.equals(day.toLocalDate());
                    return new ApiKey((UUID) rs.getObject("id"), rs.getString("label"), rs.getString("api_key"),
                            rs.getInt("daily_quota_units"), sameDay ? rs.getInt("units_used_today") : 0);
                })
                .list();
        if (enabled.isEmpty()) {
            throw new IllegalStateException("No YouTube API key: add a row to playback.youtube_api_keys or set YOUTUBE_API_KEY");
        }
        pool = enabled;
        loadedAt = clock.instant();
    }

    /** First start: the env key becomes the first row, so the table is the single source of truth from then on. */
    private void seedFromEnvIfEmpty() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) return;
        long count = jdbc.sql("SELECT count(*) FROM playback.youtube_api_keys").query(Long.class).single();
        if (count > 0) return;
        jdbc.sql("INSERT INTO playback.youtube_api_keys (label, api_key) VALUES ('env', :key) ON CONFLICT (api_key) DO NOTHING")
                .param("key", properties.apiKey())
                .update();
        log.info("Seeded playback.youtube_api_keys with the YOUTUBE_API_KEY from the environment");
    }
}

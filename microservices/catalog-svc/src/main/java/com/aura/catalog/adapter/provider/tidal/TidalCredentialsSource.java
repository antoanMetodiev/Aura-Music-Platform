package com.aura.catalog.adapter.provider.tidal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Which TIDAL client credentials to use right now. {@code catalog.tidal_api_keys} is the source of
 * truth: every call picks one of its enabled, un-benched rows <em>at random</em>, so load spreads
 * evenly over however many keys are in there. The env-configured pair is only a bootstrap — on the
 * first start it is inserted as a row (label {@code env}) and never read again while the table has
 * keys. {@link #penalize} benches a key that just got rate-limited or rejected for a few minutes.
 */
@Component
public class TidalCredentialsSource {

    private static final Logger log = LoggerFactory.getLogger(TidalCredentialsSource.class);
    private static final Duration RELOAD_INTERVAL = Duration.ofMinutes(1);
    private static final Duration PENALTY = Duration.ofMinutes(5);

    public record Credentials(UUID id, String label, String clientId, String clientSecret) {
        boolean fromDatabase() {
            return id != null;
        }
    }

    private final JdbcClient jdbc;
    private final TidalProperties properties;
    private final Clock clock;

    private volatile List<Credentials> pool = List.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public TidalCredentialsSource(JdbcClient jdbc, TidalProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /** A random member of the active pool — a fresh pick on every call. */
    public Credentials current() {
        List<Credentials> active = activePool();
        return active.get(ThreadLocalRandom.current().nextInt(active.size()));
    }

    /** Moves to the next key and benches the current one for a while. No-op with a single key. */
    public synchronized void penalize(Credentials failed, String reason) {
        if (failed.fromDatabase()) {
            jdbc.sql("UPDATE catalog.tidal_api_keys SET disabled_until = :until WHERE id = :id")
                    .param("until", clock.instant().plus(PENALTY))
                    .param("id", failed.id())
                    .update();
            loadedAt = Instant.EPOCH;
        }
        List<Credentials> active = activePool();
        if (active.stream().anyMatch(c -> !c.equals(failed))) {
            log.warn("TIDAL key '{}' benched for {} ({}); {} key(s) remain in rotation", failed.label(), PENALTY, reason, active.size());
        } else {
            log.debug("TIDAL key '{}' failed ({}), no other key to switch to", failed.label(), reason);
        }
    }

    public void markUsed(Credentials credentials) {
        if (!credentials.fromDatabase()) return;
        jdbc.sql("UPDATE catalog.tidal_api_keys SET last_used_at = now() WHERE id = :id")
                .param("id", credentials.id())
                .update();
    }

    public int poolSize() {
        return activePool().size();
    }

    private List<Credentials> activePool() {
        if (clock.instant().isAfter(loadedAt.plus(RELOAD_INTERVAL))) reload();
        return pool;
    }

    private synchronized void reload() {
        if (!clock.instant().isAfter(loadedAt.plus(RELOAD_INTERVAL))) return;
        seedFromEnvIfEmpty();
        record Row(Credentials credentials, boolean benched) {
        }
        List<Row> enabled = jdbc.sql("""
                        SELECT id, label, client_id, client_secret,
                               (disabled_until IS NOT NULL AND disabled_until > now()) AS benched
                        FROM catalog.tidal_api_keys
                        WHERE enabled
                        ORDER BY priority, created_at
                        """)
                .query((rs, n) -> new Row(new Credentials((UUID) rs.getObject("id"), rs.getString("label"),
                        rs.getString("client_id"), rs.getString("client_secret")), rs.getBoolean("benched")))
                .list();
        // A benched key is still better than no key at all — only skip it while an alternative exists.
        List<Credentials> fromDb = enabled.stream().filter(r -> !r.benched()).map(Row::credentials).toList();
        if (fromDb.isEmpty()) fromDb = enabled.stream().map(Row::credentials).toList();

        if (fromDb.isEmpty()) {
            throw new IllegalStateException(
                    "No TIDAL credentials: add a row to catalog.tidal_api_keys or set TIDAL_CLIENT_ID/TIDAL_CLIENT_SECRET");
        }
        pool = fromDb;
        loadedAt = clock.instant();
    }

    /** First start: the env pair becomes the first row, so the table is the single source of truth from then on. */
    private void seedFromEnvIfEmpty() {
        if (properties.clientId() == null || properties.clientId().isBlank()
                || properties.clientSecret() == null || properties.clientSecret().isBlank()) return;
        long count = jdbc.sql("SELECT count(*) FROM catalog.tidal_api_keys").query(Long.class).single();
        if (count > 0) return;
        jdbc.sql("""
                        INSERT INTO catalog.tidal_api_keys (id, label, client_id, client_secret)
                        VALUES (:id, 'env', :clientId, :clientSecret)
                        ON CONFLICT (client_id) DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("clientId", properties.clientId())
                .param("clientSecret", properties.clientSecret())
                .update();
        log.info("Seeded catalog.tidal_api_keys with the TIDAL_CLIENT_ID from the environment");
    }
}
